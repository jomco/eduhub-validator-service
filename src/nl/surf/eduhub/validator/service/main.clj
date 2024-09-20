;; This file is part of eduhub-validator-service
;;
;; Copyright (C) 2022 SURFnet B.V.
;;
;; This program is free software: you can redistribute it and/or
;; modify it under the terms of the GNU Affero General Public License
;; as published by the Free Software Foundation, either version 3 of
;; the License, or (at your option) any later version.
;;
;; This program is distributed in the hope that it will be useful, but
;; WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
;; Affero General Public License for more details.
;;
;; You should have received a copy of the GNU Affero General Public
;; License along with this program.  If not, see
;; <https://www.gnu.org/licenses/>.

(ns nl.surf.eduhub.validator.service.main
  (:gen-class)
  (:require [babashka.http-client :as http]
            [babashka.json :as json]
            [clojure.string :as str]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [nl.jomco.envopts :as envopts]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.authentication :as auth]
            [nl.surf.eduhub.validator.service.config :as config]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn validate-endpoint [endpoint-id {:keys [gateway-url gateway-basic-auth ooapi-version] :as _config}]
  {:pre [gateway-url]}
  (try
    (let [opts {:headers {"x-route" (str "endpoint=" endpoint-id)
                          "accept" (str "application/json; version=" ooapi-version)
                          "x-envelope-response" "true"}
                :basic-auth gateway-basic-auth
                :throw false}
          url (str gateway-url (if (.endsWith gateway-url "/") "" "/") "courses")
          {:keys [status body]} (http/get url opts)]

      ;; If the client credentials for the validator are incorrect, the wrap-allowed-clients-checker
      ;; middleware has already returned 401 forbidden and execution doesn't get here.

      (condp = status
        http-status/unauthorized
        ;; If the validator doesn't have the right credentials for the gateway, manifested by a 401 response,
        ;; we'll return a 502 error and log it.
        {:status http-status/bad-gateway :body {:valid false
                                                :message "Incorrect credentials for gateway"}}

        ;; If the gateway returns OK we assume we've gotten a json envelope response and check the response status
        ;; of the endpoint.
        http-status/ok
        (let [envelope        (json/read-str body)
              envelope-status (get-in envelope [:gateway :endpoints (keyword endpoint-id) :responseCode])]
          {:status http-status/ok
           :body   (if (= "200" (str envelope-status))
                     {:valid true}
                     ;; If the endpoint denied the gateway's request or otherwise returned a response outside the 200 range
                     ;; we return 200 ok and return the status of the endpoint and the message of the gateway in our response
                     {:valid false
                      :message (str "Endpoint validation failed with status: " envelope-status)})})

        ;; If the gateway returns something else than a 200 or a 401, treat it similar to an error
        (do
          (log/error "Unexpected response status received from gateway: " status)
          {:status http-status/internal-server-error
           :body   {:valid   false
                    :message (str "Unexpected response status received from gateway: " status)}})))
    (catch Throwable e
      (log/error e "Exception in validate-endpoint")
      {:status http-status/internal-server-error
       :body {:valid false
              :message "Internal error in validator-service"}})))

(defroutes app-routes
  (GET "/endpoints/:endpoint-id/config" [endpoint-id]
    (fn [_] {:validator true :endpoint-id endpoint-id}))
  (route/not-found "Not Found"))

(defn wrap-validator [app config]
  (fn [req]
    (let [{:keys [validator endpoint-id] :as resp} (app req)]
      (if validator
        (validate-endpoint endpoint-id config)
        resp))))

(defn start-server [routes]
  (let [server (run-jetty routes {:port 3002 :join? false})
        handler ^Runnable (fn [] (.stop server))]
    ;; Add a shutdown hook to stop Jetty on JVM exit (Ctrl+C)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. handler))
    server))

(defn -main [& _]
  (let [[config errs] (config/load-config-from-env env)]
    (when errs
      (.println *err* "Error in environment configuration")
      (.println *err* (envopts/errs-description errs))
      (.println *err* "Available environment vars:")
      (.println *err* (envopts/specs-description config/opt-specs))
      (System/exit 1))
    (let [introspection-endpoint (:introspection-endpoint-url config)
          introspection-auth     (:introspection-basic-auth config)
          allowed-client-id-set  (set (str/split (:allowed-client-ids config) #","))]
      (start-server (-> app-routes
                        (wrap-validator config)
                        (auth/wrap-allowed-clients-checker allowed-client-id-set)
                        (auth/wrap-authentication introspection-endpoint introspection-auth)
                        wrap-json-response
                        (wrap-defaults api-defaults))))))

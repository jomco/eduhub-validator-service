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
            [clojure.string :as str]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.authentication :as auth]
            [nl.surf.eduhub.validator.service.config :as config]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn validate-endpoint [endpoint-id {:keys [gateway-url gateway-basic-auth ooapi-version] :as _config}]
  (log/info "validating endpoint: " endpoint-id)
  (try
    (let [response (http/get (str gateway-url "courses")
                             {:headers {"x-route" (str "endpoint=" endpoint-id)
                                        "accept" (str "application/json; version=" ooapi-version)
                                        "x-envelope-response" "false"}
                              :basic-auth gateway-basic-auth
                              :throw false})]
      (if (= (:status response) 200)
        {:valid true}
        {:valid false :message (str "Endpoint validation failed with status: " (:status response))}))
    (catch Throwable e
      (log/error e "Exception in validate-endpoint")
      {:valid false :error true :message (str "Error during validation " (class e) ":" (ex-message e))})))

(defroutes app-routes
  (GET "/endpoints/:endpoint-id/config" [endpoint-id]
    (fn [_] {:validator true :endpoint-id endpoint-id}))
  (route/not-found "Not Found"))

(defn wrap-validator [app config]
  (fn [req]
    (let [{:keys [validator endpoint-id] :as resp} (app req)]
      (if validator
        (let [{:keys [error] :as body} (validate-endpoint endpoint-id config)]
          {:body body
           :status (if error
                     http-status/internal-server-error
                     http-status/ok)})
        resp))))

(defn start-server [routes]
  (let [server (run-jetty routes {:port 3002 :join? false})
        handler ^Runnable (fn [] (.stop server))]
    ;; Add a shutdown hook to stop Jetty on JVM exit (Ctrl+C)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. handler))
    server))

(defn -main [& _]
  (let [config (config/load-config-from-env)
        introspection-endpoint (:introspection-endpoint-url config)
        introspection-auth (:introspection-basic-auth config)
        allowed-client-id-set (set (str/split (:allowed-client-ids config) #","))]
    (start-server (-> app-routes
                      (wrap-validator config)
                      (auth/wrap-allowed-clients-checker allowed-client-id-set)
                      (auth/wrap-authentication introspection-endpoint introspection-auth)
                      wrap-json-response
                      (wrap-defaults api-defaults)))))

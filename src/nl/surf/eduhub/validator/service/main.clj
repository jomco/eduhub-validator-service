(ns nl.surf.eduhub.validator.service.main
  (:gen-class)
  (:require [babashka.http-client :as http]
            [clojure.string :as string]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            [nl.jomco.http-status-codes :as http-status]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]))

(def gateway-url (System/getenv "GATEWAY_URL"))
(def gateway-auth
  (let [[user pass] (string/split (System/getenv "GATEWAY_BASIC_AUTH") #":")]
    {:user user :pass pass}))

(defn validate-endpoint [endpoint-id]
  (log/info "validating endpoint: " endpoint-id)
  (try
    (let [response (http/get (str gateway-url "courses")
                             {:headers {"x-route" (str "endpoint=" endpoint-id)
                                        "accept" "application/json; version=5"
                                        "x-envelope-response" "false"}
                              :basic-auth gateway-auth})]
      (if (= (:status response) 200)
        {:valid true :message "Endpoint validation successful"}
        {:valid false :message (str "Endpoint validation failed with status: " (:status response))}))
    (catch Throwable e
      {:valid false :message (str "Error during validation: " (.getMessage e))})))

(defroutes app-routes
  (GET "/endpoints/:endpoint-id/config" [endpoint-id]
    (fn [_]
      (let [{:keys [message valid]} (validate-endpoint endpoint-id)]
        {:body message
         :status (if valid
                   http-status/ok
                   http-status/bad-gateway)})))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      wrap-json-response))

(defn -main [& _]
  (run-jetty app {:port 3000}))

(ns nl.surf.eduhub.validator.service.main
  (:gen-class)
  (:require [babashka.http-client :as http]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            [environ.core :refer [env]]
            [nl.jomco.envopts :as envopts]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.authentication :as auth]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn validate-endpoint [endpoint-id config]
  (log/info "validating endpoint: " endpoint-id)
  (try
    (let [response (http/get (str (:gateway-url config) "courses")
                             {:headers {"x-route" (str "endpoint=" endpoint-id)
                                        "accept" "application/json; version=5"
                                        "x-envelope-response" "false"}
                              :basic-auth (:gateway-basic-auth config)})]
      (if (= (:status response) 200)
        {:valid true}
        {:valid false :message (str "Endpoint validation failed with status: " (:status response))}))
    (catch Throwable e
      {:valid false :message (str "Error during validation: " (.getMessage e))})))

(defroutes app-routes
  (GET "/endpoints/:endpoint-id/config" [endpoint-id]
    (fn [_] {:validator true :endpoint-id endpoint-id}))
  (route/not-found "Not Found"))

(defn wrap-validator [app config]
  (fn [req]
    (let [{:keys [validator endpoint-id] :as resp} (app req)]
      (if validator
        (let [{:keys [valid] :as body} (validate-endpoint endpoint-id config)]
          {:body body
           :status (if valid
                     http-status/ok
                     http-status/bad-gateway)})
        resp))))

(def opt-specs
  {:gateway-url             ["URL of gateway" :str :in [:gateway-url]]
   :gateway-basic-auth-user ["Basic auth username of gateway" :str :in [:gateway-basic-auth :user]]
   :gateway-basic-auth-pass ["Basic auth password of gateway" :str :in [:gateway-basic-auth :pass]]
   :introspection-client-id ["Basic auth username of introspection" :str :in [:introspection-basic-auth :user]]
   :introspection-secret    ["Basic auth password of introspection" :str :in [:introspection-basic-auth :pass]]
   :introspection-endpoint  ["Introspection endpoint url" :str :in [:introspection-endpoint-url]]})

(defn start-server [routes]
  (let [server (run-jetty routes {:port 3002 :join? false})
        handler ^Runnable (fn [] (.stop server))]
    ;; Add a shutdown hook to stop Jetty on JVM exit (Ctrl+C)
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. handler))
    server))

(defn -main [& _]
  (let [[config errs] (envopts/opts env opt-specs)
        introspection-endpoint (:introspection-endpoint-url config)
        introspection-auth (:introspection-basic-auth config)]
    (when errs
      (.println *err* "Error in environment configuration")
      (.println *err* (envopts/errs-description errs))
      (.println *err* "Available environment vars:")
      (.println *err* (envopts/specs-description opt-specs))
      (System/exit 1))
    (start-server (-> app-routes
                      (wrap-validator config)
                      (auth/wrap-authentication introspection-endpoint introspection-auth)
                      (wrap-defaults api-defaults)
                      wrap-json-response))))

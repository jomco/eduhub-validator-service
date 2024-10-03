(ns nl.surf.eduhub.validator.service.jobs.worker
  (:require [goose.brokers.redis.broker :as broker]
            [goose.worker :as w]
            [nl.surf.eduhub.validator.service.config :as config]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.validate :as validate]))

(def worker (w/start (assoc w/default-opts
                       :broker (broker/new-consumer broker/default-opts))))

;; opts should contain: basic-auth ooapi-version base-url profile
(defn validate-endpoint [endpoint-id uuid opts]
  (let [{:keys [redis-conn]} @config/config-atom]
    (try
      (let [html (validate/validate-endpoint endpoint-id opts)]
        ;; assuming everything went ok, save html in status, update status and set expiry to value configured in ENV
        (status/set-status-fields redis-conn uuid "finished" {"html-report" html} (:expiry-seconds @config/config-atom)))
      (catch Exception ex
        ;; otherwise set status to error, include error message and also set expiry
        (status/set-status-fields redis-conn uuid "failed" {"error" (str ex)} (:expiry-seconds @config/config-atom))))))

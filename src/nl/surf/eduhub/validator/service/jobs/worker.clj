(ns nl.surf.eduhub.validator.service.jobs.worker
  (:require [clojure.tools.logging :as log]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.validate :as validate]))

;; A worker thread running in the background
;; Called by the workers. Runs the validate-endpoint function
;; and updates the values in the job status.
;; opts should contain: basic-auth ooapi-version base-url profile
(defn validate-endpoint [endpoint-id uuid {:keys [config] :as opts}]
  (let [{:keys [redis-conn expiry-seconds]} config]
    (try
      (let [html (validate/validate-endpoint endpoint-id opts)]
        ;; assuming everything went ok, save html in status, update status and set expiry to value configured in ENV
        (status/set-status-fields redis-conn uuid "finished" {"html-report" html} expiry-seconds))
      (catch Exception ex
        ;; otherwise set status to error, include error message and also set expiry
        (log/error ex "Validate endpoint threw an exception")
        (status/set-status-fields redis-conn uuid "failed" {"error" (str ex)} expiry-seconds)))))

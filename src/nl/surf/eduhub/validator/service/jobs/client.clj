(ns nl.surf.eduhub.validator.service.jobs.client
  (:require [clojure.tools.logging :as log]
            [goose.brokers.redis.broker :as broker]
            [goose.client :as c]
            [goose.retry :as retry]
            [nl.surf.eduhub.validator.service.config :as config]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [nl.surf.eduhub.validator.service.jobs.worker :as worker])
  (:import [java.util UUID]))

(defn job-error-handler [_cfg _job ex]
  (log/error ex "Error in job"))

(def client-opts (assoc c/default-opts
                   :broker (broker/new-producer broker/default-opts)
                   :retry-opts (assoc retry/default-opts :error-handler-fn-sym `job-error-handler)))

(defn enqueue-endpoint [endpoint-id profile]
  (let [{:keys [redis-conn] :as config} @config/config-atom
        uuid (UUID/randomUUID)
        prof (or profile "rio")
        opts {:basic-auth    (:gateway-basic-auth config)
              :base-url      (:gateway-url config)
              :ooapi-version (:ooapi-version config)
              :profile       prof}]
    (status/set-status-fields redis-conn uuid "pending" {:endpoint-id endpoint-id, :profile prof} nil)
    (c/perform-async client-opts `worker/validate-endpoint endpoint-id (str uuid) opts)
    {:status 200 :body {:job-status "pending" :uuid uuid}}))

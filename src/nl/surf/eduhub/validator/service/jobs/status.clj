(ns nl.surf.eduhub.validator.service.jobs.status
  (:require [taoensso.carmine :as car])
  (:import [java.time Instant]))

(defn status-key [uuid] (str "job-status:" uuid))

(def job-status "job-status")

(defn expires [redis-conn uuid expires-in-seconds]
  (car/wcar redis-conn (car/expire (status-key uuid) expires-in-seconds)))

(defn set-status [redis-conn uuid new-value]
  (car/wcar redis-conn (car/hset (status-key uuid) job-status new-value (str new-value "-at") (System/currentTimeMillis))))

(defn set-key-pair [redis-conn uuid key-name value]
  (car/wcar redis-conn (car/hset (status-key uuid) key-name value)))

(defn set-status-fields [redis-conn uuid new-status key-value-map expires-in-seconds]
  (let [x (assoc key-value-map
            job-status new-status
            (str new-status "-at") (System/currentTimeMillis))
        key (status-key uuid)]
    (car/wcar redis-conn (car/hmset* key x))
    (when expires-in-seconds
      (car/wcar redis-conn (car/expire key expires-in-seconds)))))

(defn load-status [redis-conn uuid]
  (let [result (car/wcar redis-conn (car/hgetall (status-key uuid)))]
    (when-not (empty? result)
      (into {}
            (map (fn [[k v]]
                   (if (and (string? k) (.endsWith k "-at"))
                     [k (str (Instant/ofEpochMilli (Long/parseLong v)))]
                     [k v]))
                 (apply hash-map result))))))

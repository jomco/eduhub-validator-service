;; This file is part of eduhub-validator-service
;;
;; Copyright (C) 2024 SURFnet B.V.
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

(ns nl.surf.eduhub.validator.service.api
  (:require [clojure.string :as str]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.route :as route]
            [nl.jomco.http-status-codes :as http-status]
            [nl.surf.eduhub.validator.service.authentication :as auth]
            [nl.surf.eduhub.validator.service.checker :as checker]
            [nl.surf.eduhub.validator.service.jobs.client :as jobs-client]
            [nl.surf.eduhub.validator.service.jobs.status :as status]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]))

(defroutes app-routes
           (GET "/status/:uuid" [uuid]
             {:load-status true, :uuid uuid})
           (POST "/endpoints/:endpoint-id/config" [endpoint-id]
             {:checker true :endpoint-id endpoint-id})
           (POST "/endpoints/:endpoint-id/paths" [endpoint-id profile]
             {:validator true :endpoint-id endpoint-id :profile profile})
           (route/not-found "Not Found"))

;; Many response handlers have the same structure - with this function they can be written inline.
;; `activate-handler?` is a function that takes a request and returns a boolean which determines if
;; the current handler should be activated (or skipped).
;; `response-handler` takes an intermediate response and processes it into the next step.
(defn wrap-response-handler [app activate-handler? response-handler]
  (fn [req]
    (let [resp (app req)]
      (if (activate-handler? resp)
        (response-handler resp)
        resp))))

;; Turn the contents of a job status (stored in redis) into an http response.
(defn- job-status-handler [{:keys [redis-conn] :as _config}]
  (fn [resp]
    (let [job-status (status/load-status redis-conn (:uuid resp))]
      (if (empty? job-status)
        {:status http-status/not-found}
        {:status http-status/ok :body job-status}))))

;; Compose the app from the routes and the wrappers. Authentication can be disabled for testing purposes.
(defn compose-app [{:keys [introspection-endpoint-url introspection-basic-auth allowed-client-ids] :as config} auth-enabled]
  (let [allowed-client-id-set  (set (str/split allowed-client-ids #","))
        auth-opts              {:auth-enabled (boolean auth-enabled)}]
    (-> app-routes
        (wrap-response-handler :checker    #(checker/check-endpoint (:endpoint-id %) config))
        (wrap-response-handler :validator  #(jobs-client/enqueue-validation (:endpoint-id %) (:profile %) config))
        (wrap-response-handler :load-status (job-status-handler config))
        (auth/wrap-allowed-clients-checker allowed-client-id-set auth-opts)
        (auth/wrap-authentication introspection-endpoint-url introspection-basic-auth auth-opts)
        wrap-json-response
        (wrap-defaults api-defaults))))

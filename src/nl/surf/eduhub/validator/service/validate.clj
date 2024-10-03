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

(ns nl.surf.eduhub.validator.service.validate
  (:gen-class)
  (:require [babashka.http-client :as http]
            [nl.jomco.apie.main :as apie])
  (:import [java.io File]))

(defn check-endpoint
  "Performs a synchronous validation via the eduhub-validator"
  [endpoint-id {:keys [gateway-url gateway-basic-auth ooapi-version] :as _config}]
  {:pre [gateway-url]}
  (let [url (str gateway-url (if (.endsWith gateway-url "/") "" "/") "courses")
        opts {:headers {"x-route" (str "endpoint=" endpoint-id)
                        "accept" (str "application/json; version=" ooapi-version)
                        "x-envelope-response" "true"}
              :basic-auth gateway-basic-auth
              :throw false}]
    (http/get url opts)))

(defn- temp-file [fname ext]
  (let [tmpfile (File/createTempFile fname ext)]
    (.deleteOnExit tmpfile)
    tmpfile))

(defn validate-endpoint
  "Returns the HTML validation report as a String."
  [endpoint-id {:keys [basic-auth ooapi-version base-url profile] :as opts}]
  {:pre [endpoint-id basic-auth ooapi-version base-url profile]}
  (let [report-file       (temp-file "report" ".html")
        report-path       (.getAbsolutePath report-file)
        observations-file (temp-file "observations" ".edn")
        observations-path (.getAbsolutePath observations-file)
        defaults {:bearer-token nil,
                  :no-report? false,
                  :max-total-requests 5,
                  :report-path report-path,
                  :headers {:x-route (str "endpoint=" endpoint-id),
                            :accept (str "application/json; version=" ooapi-version),
                            :x-envelope-response "false"},
                  :no-spider? false,
                  :max-requests-per-operation ##Inf,
                  :observations-path observations-path,
                  :profile profile}]
    (try
      (apie/main (merge defaults opts))
      (slurp report-path)
      (finally
        (.delete observations-file)
        (.delete report-file)))))

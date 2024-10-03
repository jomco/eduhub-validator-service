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

(ns nl.surf.eduhub.validator.service.config
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [nl.jomco.envopts :as envopts]))

(def opt-specs
  {:gateway-url                        ["URL of gateway" :str
                                        :in [:gateway-url]]
   :gateway-basic-auth-user            ["Basic auth username of gateway" :str
                                        :in [:gateway-basic-auth :user]]
   :gateway-basic-auth-pass            ["Basic auth password of gateway" :str
                                        :in [:gateway-basic-auth :pass]]
   :allowed-client-ids                 ["Comma separated list of allowed SurfCONEXT client ids." :str
                                        :in [:allowed-client-ids]]
   :surf-conext-client-id              ["SurfCONEXT client id for validation service" :str
                                        :in [:introspection-basic-auth :user]]
   :surf-conext-client-secret          ["SurfCONEXT client secret for validation service" :str
                                        :in [:introspection-basic-auth :pass]]
   :surf-conext-introspection-endpoint ["SurfCONEXT introspection endpoint" :str
                                        :in [:introspection-endpoint-url]]
   :redis-uri                          ["URI to redis" :str
                                        :default "redis://localhost"
                                        :in [:redis-conn :spec :uri]]
   :server-port                        ["Starts the app server on this port" :int]
   :ooapi-version                      ["Ooapi version to pass through to gateway" :str
                                        :in [:ooapi-version]]})

;; There seems to be no way to pass the config to the worker except via a global var
(def config-atom (atom nil))

(defn- file-secret-loader-reducer [env-map value-key]
  (let [file-key (keyword (str (name value-key) "-file"))
        path (file-key env-map)]
    (cond
      (nil? path)
      env-map

      (not (.exists (io/file path)))
      (throw (ex-info (str "ENV var contains filename that does not exist: " path)
                      {:filename path, :env-path file-key}))

      (value-key env-map)
      (throw (ex-info "ENV var contains secret both as file and as value"
                      {:env-path [value-key file-key]}))

      :else
      (-> env-map
          (assoc value-key (str/trim (slurp path)))
          (dissoc file-key)))))

;; These ENV keys may alternatively have a form in which the secret is contained in a file.
;; These ENV keys have a -file suffix, e.g.: gateway-basic-auth-pass-file
(def env-keys-with-alternate-file-secret
  [:gateway-basic-auth-user :gateway-basic-auth-pass :surf-conext-client-id :surf-conext-client-secret])

(defn load-config-from-env [env-map]
  (-> (reduce file-secret-loader-reducer env-map env-keys-with-alternate-file-secret)
      (envopts/opts opt-specs)))

(defn validate-and-load-config [env]
  (let [[config errs] (load-config-from-env env)]
    (when errs
      (.println *err* "Error in environment configuration")
      (.println *err* (envopts/errs-description errs))
      (.println *err* "Available environment vars:")
      (.println *err* (envopts/specs-description opt-specs))
      (System/exit 1))
    config))

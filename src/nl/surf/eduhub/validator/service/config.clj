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

(ns nl.surf.eduhub.validator.service.config
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [environ.core :refer [env]]
            [nl.jomco.envopts :as envopts]))

(def opt-specs
  {:gateway-url                        ["URL of gateway" :str
                                        :in [:gateway-url]]
   :gateway-basic-auth-user            ["Basic auth username of gateway" :str
                                        :default nil
                                        :in [:gateway-basic-auth :user]]
   :gateway-basic-auth-pass            ["Basic auth password of gateway" :str
                                        :default nil
                                        :in [:gateway-basic-auth :pass]]
   :gateway-basic-auth-user-file       ["Basic auth username of gateway, stored in secret file" :str
                                        :default nil
                                        :in [:gateway-basic-auth :user-file]]
   :gateway-basic-auth-pass-file       ["Basic auth password of gateway, stored in secret file" :str
                                        :default nil
                                        :in [:gateway-basic-auth :pass-file]]
   :allowed-client-ids                 ["Comma separated list of allowed SurfCONEXT client ids." :str
                                        :in [:allowed-client-ids]]
   :surf-conext-client-id              ["SurfCONEXT client id for validation service" :str
                                        :default nil
                                        :in [:introspection-basic-auth :user]]
   :surf-conext-client-secret          ["SurfCONEXT client secret for validation service" :str
                                        :default nil
                                        :in [:introspection-basic-auth :pass]]
   :surf-conext-client-id-file         ["SurfCONEXT client id for validation service, stored in secret file" :str
                                        :default nil
                                        :in [:introspection-basic-auth :user-file]]
   :surf-conext-client-secret-file     ["SurfCONEXT client secret for validation service, stored in secret file" :str
                                        :default nil
                                        :in [:introspection-basic-auth :pass-file]]
   :surf-conext-introspection-endpoint ["SurfCONEXT introspection endpoint" :str
                                        :in [:introspection-endpoint-url]]
   :ooapi-version                      ["Ooapi version to pass through to gateway" :str
                                        :in [:ooapi-version]]})

(def key-value-pairs-with-optional-secret-files
  {:gateway-basic-auth-user [:gateway-basic-auth :user]
   :gateway-basic-auth-pass [:gateway-basic-auth :pass]
   :surf-conext-client-id [:introspection-basic-auth :user]
   :surf-conext-client-secret [:introspection-basic-auth :pass]})

(defn- validate-required-secrets
  " If a key \"K\" in `opt-specs` is not present, and a key \"K-file\"\n  is present,
  load the secret from that file and put it in the env map\n  under K."
  [config]
  (let [missing-env (reduce
                      (fn [m [k v]] (if (get-in config v)
                                      m
                                      (assoc m k "missing")))
                      {}
                      key-value-pairs-with-optional-secret-files)]
    (when (not-empty missing-env)
      (.println *err* "Configuration error")
      (.println *err* (envopts/errs-description missing-env))
      (System/exit 1))
    config))

(defn dissoc-in
  "Return nested map with path removed."
  [m ks]
  (let [path (butlast ks)
        node (last ks)]
    (if (empty? path)
      (dissoc m node)
      (update-in m path dissoc node))))

(defn- load-secret-from-file [config k]
  (let [file-key-node (keyword (str (name (last k)) "-file")) ; The last entry in the :in array, with a "-file" suffix added
        root-key-path (pop k)                               ; The :in array without the last item
        file-key-path (conj root-key-path file-key-node)    ; The :in array with the last item having a "-file" suffix
        path (get-in config file-key-path)                  ; File path to secret
        config (dissoc-in config file-key-path)]            ; Remove -file key from config
    (if (nil? path)
      config
      (if (.exists (io/file path))
        (assoc-in config k (str/trim (slurp path)))         ; Overwrite config with secret from file
        (throw (ex-info (str "ENV var contains filename that does not exist: " path)
                        {:filename path, :env-path k}))))))

(defn load-config-from-env []
  (let [[config errs] (envopts/opts env opt-specs)]
    (when errs
      (.println *err* "Error in environment configuration")
      (.println *err* (envopts/errs-description errs))
      (.println *err* "Available environment vars:")
      (.println *err* (envopts/specs-description opt-specs))
      (System/exit 1))
    (->> key-value-pairs-with-optional-secret-files
         vals
         (reduce load-secret-from-file config)
         validate-required-secrets)))

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

(ns nl.surf.eduhub.validator.service.main-test
  (:require [babashka.http-client :as http]
            [clojure.test :refer [deftest is]]
            [nl.surf.eduhub.validator.service.main :as main]))

(def app (main/wrap-validator main/app-routes {:gateway-url "http://gateway.dev.surf.nl"}))

(deftest test-validate-correct
  (with-redefs [http/request (fn [_] {:status 200 :body "mocked response"})]
    (is (= {:status 200
            :body {:valid true} }
           (app {:uri "/endpoints/google.com/config" :request-method :get})))))

(deftest test-validate-fails
  (with-redefs [http/request (fn [_] {:status 401 :body "mocked response"})]
    (is (= {:status 200
            :body {:message "Endpoint validation failed with status: 401", :valid false}}
           (app {:uri "/endpoints/google.com/config" :request-method :get})))))

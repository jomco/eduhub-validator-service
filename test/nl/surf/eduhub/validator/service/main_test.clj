(ns nl.surf.eduhub.validator.service.main-test
  (:require [babashka.http-client :as http]
            [clojure.test :refer [deftest is]]
            [nl.surf.eduhub.validator.service.main :as main]))

(def app (main/wrap-validator main/app-routes {}))

(deftest test-validate-correct
  (with-redefs [http/request (fn [_] {:status 200 :body "mocked response"})]
    (is (= {:status 200
            :body {:valid true} }
           (app {:uri "/endpoints/google.com/config" :request-method :get})))))

(deftest test-validate-fails
  (with-redefs [http/request (fn [_] {:status 401 :body "mocked response"})]
    (is (= {:status 502
            :body {:message "Endpoint validation failed with status: 401", :valid false}}
           (app {:uri "/endpoints/google.com/config" :request-method :get})))))

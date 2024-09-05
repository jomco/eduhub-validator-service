(ns nl.surf.eduhub-validator-service.main-test
  (:require [clojure.test :refer :all]
            [nl.surf.eduhub-validator-service.main :as main]))

(deftest test-validate-correct
  (with-redefs [babashka.http-client/get (fn [& _] {:status 200 :body "mocked response"})]
    (is (= {:status 200 :body "Endpoint validation successful" :headers {}}
           (main/app-routes {:uri "/endpoints/google.com/config" :request-method :get})))))

(deftest test-validate-fails
  (with-redefs [babashka.http-client/get (fn [& _] {:status 401 :body "mocked response"})]
    (is (= {:status 502 :body "Endpoint validation failed with status: 401" :headers {}}
           (main/app-routes {:uri "/endpoints/google.com/config" :request-method :get})))))

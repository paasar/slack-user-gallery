(ns slack-user-gallery.core-test
  (:require [clj-slack.channels :as slack-channels]
            [clj-slack.users :as slack-users]
            [clojure.test :refer :all]
            [slack-user-gallery.core :refer :all]))

(def ^:private members [{:is_restricted false
                         :id "USER1"
                         :name "U1_NAME"
                         :profile {:display_name "U1_DISPLAY_NAME"
                                   :real_name "U1_REAL_NAME"
                                   :image_64 "U1_IMAGE_64"
                                   :image_128 "U1_IMAGE_128"}}
                        {:is_restricted false
                         :id "USER2"
                         :name "U2_NAME"
                         :profile {:display_name "U2_DISPLAY_NAME"
                                   :real_name "U2_REAL_NAME"
                                   :image_128 "U2_IMAGE_128"
                                   :image_original "U2_IMAGE_ORIGINAL"}}])

(def ^:private valid-user-data [{:nick "U2_DISPLAY_NAME"
                                 :real "U2_REAL_NAME"
                                 :pic "U2_IMAGE_ORIGINAL"
                                 :start-time (->date "1404230000.000000")}
                                {:nick "U1_DISPLAY_NAME"
                                 :real "U1_REAL_NAME"
                                 :pic "U1_IMAGE_128"
                                 :start-time (->date "1404219430.000000")}])

(deftest with-ok-response-on-fetch-users
  (with-redefs [slack-users/list (constantly {:ok true :data {:stuff 1}})]
    (testing "whole response is returned"
      (is (= {:ok true :data {:stuff 1}} (fetch-users))))))

(deftest with-not-ok-response-on-fetch-users
  (with-redefs [slack-users/list (constantly {:ok false :error "Something went wrong"})]
    (testing "Exception is thrown with error message in response"
      (is (thrown-with-msg? Exception #"Something went wrong" (fetch-users))))))

(deftest with-valid-message-history
  (let [history (atom [{:ok true
                        :has_more true
                        :messages [{:user "USER1"
                                    :ts "1404219430.000000"}
                                   {:user "USER1"
                                    :ts "1404220000.000000"}
                                   {:user "missing ts"}]}
                       {:ok true
                        :has_more false
                        :messages [{:user "USER2"
                                    :ts "1404230000.000000"}
                                   {:user "USER1"
                                    :ts "1404230001.000000"}]}])]
    (with-redefs [slack-channels/history (fn [& _]
                                           (let [[fst & rst] @history]
                                             (reset! history rst)
                                             fst))]
      (is (= {"USER1" {:start-time (->date "1404219430.000000")}
              "USER2" {:start-time (->date "1404230000.000000")}}
             (get-start-times-from-general-history))))))

(deftest with-invalid-history-response
  (with-redefs [slack-channels/history (constantly {:ok false
                                                    :message "Something went wrong"})]
    (testing "Exception is thrown with message in response"
      (is (thrown-with-msg? Exception #"Something went wrong"
                            (get-start-times-from-general-history))))))

(deftest with-user-and-history-data
  (with-redefs [slack-channels/history (constantly {:ok true
                                                    :has_more false
                                                    :messages [{:user "USER1"
                                                                :ts "1404219430.000000"}
                                                               {:user "USER2"
                                                                :ts "1404230000.000000"}]})
                slack-users/list (constantly {:ok true
                                              :members members})]
    (is (= valid-user-data
           (create-user-data (fetch-users) (get-start-times-from-general-history))))))

(deftest with-valid-user-data
  (testing "Should render HTML"
    (is (= (slurp "test/resources/expected_gallery.html" :encoding "UTF-8")
           (render-html valid-user-data (->date "1432100000.000000"))))))


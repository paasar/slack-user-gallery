(ns slack-user-gallery.core
  (:require [clj-slack.channels :as slack-channels]
            [clj-slack.users :as slack-users]
            [clojure.edn :as edn]
            [clojure.string :as string :refer [join split starts-with?]])
  (:import (java.text SimpleDateFormat)
           (java.util Date))
  (:gen-class))

(def properties (edn/read-string (slurp "resources/properties.edn")))

(def connection {:api-url "https://slack.com/api" :token (:token properties)})

(defn- format-date [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd") date))

(defn fetch-users []
  (println "Fetching users.")
  (let [response (slack-users/list connection)
        ok (:ok response)]
    (when-not ok
      (throw (Exception. (:error response))))
    response))

(defn get-image-url
  "Sort by image size and take the biggest."
  [profile]
  (->> profile
       (filter (fn [[k _]] (starts-with? (name k) "image")))
       (remove #(= (first %) :image_original))
       (sort-by (fn [[k _]] (-> k name (split #"_") second read-string)) >)
       first
       second))

(defn add-new [m [k v]]
  (if-not (get m k)
    (assoc m k v)
    m))

(defn get-first-joins [message-history]
  (->> message-history
       :messages
       (filter #(= "channel_join" (:subtype %)))
       (map (fn [{:keys [user ts]}]
              [user
               {:start-time (-> ts
                                (split #"\.")
                                first
                                read-string
                                (* 1000)
                                (Date.))}]))
       (sort-by (fn [[_ v]] (:start-time v)))
       (reduce add-new {})))

(defn- fetch-history [options]
  (println (str "Fetching history of #general with " options))
  (let [{:keys [ok message] :as response} (slack-channels/history connection (:channel-id-general properties) options)]
    (if ok
      response
      (throw (Exception. message)))))

(defn get-start-times-from-general-history
  "Get first joins from history of #general channel since each user is added there automatically.
   I asked Slack to add start time to user data, but in the mean time this was the
   suggested workaround.
   1375315200 = Beginning of August 2013 or around when Slack was established."
  []
  (let [options {:count "1000" :oldest "1375315200"}]
    (loop [hist (fetch-history options)
           join-history {}]
      (let [new-join-history (merge-with add-new join-history (get-first-joins hist))]
        (if (:has_more hist)
          (recur (fetch-history (assoc options :oldest (->> hist :messages first :ts)))
                 new-join-history)
          new-join-history)))))

(defn interesting-data [{:keys [id name profile]}]
  [id {:nick (or (:display_name profile) name)
       :real (:real_name profile)
       :pic (if-let [original (:image_original profile)]
              original
              (get-image-url profile))}])

(defn remove-restricted-and-ignored-then-get-interesting-data [user-data]
  (->> user-data
       :members
       (remove #(or (:is_restricted %)
                    (:deleted %)
                    (:is_bot %)
                    (some (set [(:name %) (-> % :profile :display_name)]) (:ignored-users properties))))
       (map interesting-data)))

(defn add-start-times [join-history users]
  (merge-with merge users join-history))

(defn ->card [{:keys [nick real pic start-time]}]
  (str "<div class=\"card\">"
       "<div class=\"pic\"><img src=\"" pic "\"/></div>"
       "<div class=\"nick\">" nick "</div>"
       "<div class=\"real\">" real "</div>"
       "<div class=\"start-time\">" (when start-time (format-date start-time)) "</div>"
       "</div>"))

(defn render-html [user-cards]
  (println "Rendering HTML.")
  (let [card-count (str (count user-cards))
        user-tds (join "\n" user-cards)
        template (slurp "resources/template.html")]
    (-> template
        (string/replace "COUNT" card-count)
        (string/replace "TITLE" (:title properties))
        (string/replace "UPDATE" (str (Date.)))
        (string/replace "USER_LIST" user-tds))))

(defn write-to-file [content]
  (spit "gallery.html" content :encoding "UTF-8"))

(defn generate-html [user-data start-times]
  (->> user-data
       remove-restricted-and-ignored-then-get-interesting-data
       (into {})
       (add-start-times start-times)
       (sort-by (fn [[_ v]] (:start-time v)))
       reverse
       (map second)
       (remove #(empty? (:nick %)))
       (map ->card)
       render-html
       write-to-file))

(defn -main []
  (generate-html (fetch-users) (get-start-times-from-general-history)))

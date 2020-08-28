(ns slack-user-gallery.core
  (:require [clj-slack.conversations :as conversations]
            [clj-slack.users :as slack-users]
            [clojure.edn :as edn]
            [clojure.java.io :refer [output-stream resource]]
            [clojure.string :as string :refer [join split starts-with?]]
            [slack-user-gallery.image :refer [render-image]])
  (:import (java.time Instant ZonedDateTime ZoneId)
           (java.time.format DateTimeFormatter)
           (javax.imageio ImageIO))
  (:gen-class))

(def properties (edn/read-string (slurp (resource "properties.edn"))))

(def connection {:api-url "https://slack.com/api" :token (:token properties)})

(def ^:private UTC (ZoneId/of "UTC"))

(defn- format-date [instant]
  (.format DateTimeFormatter/ISO_LOCAL_DATE (ZonedDateTime/ofInstant instant UTC)))

(defn- format-date-verbose [instant]
  (.format DateTimeFormatter/RFC_1123_DATE_TIME (ZonedDateTime/ofInstant instant UTC)))

(defn fetch-users []
  (println "Fetching users.")
  (let [response (slack-users/list connection)
        ok (:ok response)]
    (when-not ok
      (throw (Exception. (:error response))))
    response))

(defn- get-image-url
  "Sort by image size and take the biggest."
  [profile]
  (->> profile
       (filter (fn [[k _]] (starts-with? (name k) "image")))
       (remove #(= (first %) :image_original))
       (sort-by (fn [[k _]] (-> k name (split #"_") second read-string)) >)
       first
       second))

(defn- add-new [m [k v]]
  (if-not (get m k)
    (assoc m k v)
    m))

(defn- user-and-ts? [{:keys [user ts]}]
  (and user ts))

(defn ->instant [ts]
  (-> ts
      (split #"\.")
      first
      read-string
      (* 1000)
      (Instant/ofEpochMilli)))

(defn- get-first-joins [message-history]
  (->> message-history
       :messages
       (filter user-and-ts?)
       (map (fn [{:keys [user ts]}]
              [user
               {:start-time (->instant ts)}]))
       (sort-by (fn [[_ v]] (:start-time v)))
       (reduce add-new {})))

(defn- fetch-history [options]
  (println (str "Fetching history of #general with " options))
  (let [{:keys [ok message] :as response} (conversations/history connection (:channel-id-general properties) options)]
    (if ok
      response
      (throw (Exception. message)))))

(defn- merge-new-joins [acc new-joins]
  (->> new-joins
       (remove (fn [[k _]] (some? (acc k))))
       (into {})
       (merge acc)))

(defn get-start-times-from-general-history
  "Get first joins from history of #general channel since each user is added there automatically.
   I asked Slack to add start time to user data, but in the mean time this was the
   suggested workaround.
   1375315200 = Beginning of August 2013 or around when Slack was established."
  []
  (let [options {:count "1000" :oldest "1375315200"}]
    (loop [hist (fetch-history options)
           join-history {}]
      (let [new-join-history (merge-with merge-new-joins join-history (get-first-joins hist))]
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

(defn- remove-restricted-and-ignored-then-get-interesting-data [user-data]
  (->> user-data
       :members
       (remove #(or (:is_restricted %)
                    (:deleted %)
                    (:is_bot %)
                    (some (set [(:name %) (-> % :profile :display_name)]) (:ignored-users properties))))
       (map interesting-data)))

(defn- add-start-times [join-history users]
  (merge-with merge users join-history))

(defn- ->card [{:keys [nick real pic start-time]}]
  (str "<div class=\"card\">"
       "<div class=\"pic\"><img src=\"" pic "\"/></div>"
       "<div class=\"nick\">" nick "</div>"
       "<div class=\"real\">" real "</div>"
       "<div class=\"start-time\">" (when start-time (format-date start-time)) "</div>"
       "</div>"))

(defn render-html [user-data & [now]]
  (println "Rendering HTML.")
  (let [user-cards (map ->card user-data)
        card-count (str (count user-cards))
        user-tds (join "\n" user-cards)
        template (slurp (resource "template.html"))]
    (-> template
        (string/replace "COUNT" card-count)
        (string/replace "TITLE" (:title properties))
        (string/replace "UPDATE" (format-date-verbose (or now (Instant/now))))
        (string/replace "USER_LIST" user-tds))))

(defn create-user-data [users-from-slack start-times]
  (->> users-from-slack
       remove-restricted-and-ignored-then-get-interesting-data
       (into {})
       (add-start-times start-times)
       (sort-by (fn [[_ v]] (:start-time v)))
       reverse
       (map second)
       (remove #(empty? (:nick %)))))

(defn- write-as-html-file [content]
  (spit "gallery.html" content :encoding "UTF-8"))

(defn- write-as-jpg-file [content]
  (with-open [os (output-stream "gallery.jpg")]
    (ImageIO/write (render-image content) "jpg" os)))

(defn- select-output-fn [output]
  (if (= output "jpg")
    write-as-jpg-file
    write-as-html-file))

(defn print-usage [output]
  (println (format "Unknown argument '%s'. Only 'jpg' is accepted." output))
  (System/exit 1))

(defn generate-gallery-html
  "This is the main function that should be used when this program
   is used as a library and desired output is HTML.

   Returns HTML as a String."
  []
  (render-html (create-user-data (fetch-users) (get-start-times-from-general-history))))

(defn generate-gallery-image
  "This is the main function that should be used when this program
   is used as a library and desired output is JPG.

   Returns java.awt.image.BufferedImage."
  []
  (render-image (generate-gallery-html)))

(defn generate-gallery [output]
  (if (and output (not= output "jpg"))
    (print-usage output)
    (let [output-fn (select-output-fn output)]
      (output-fn (generate-gallery-html)))))

(defn -main [& [output]]
  (generate-gallery output))

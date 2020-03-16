(ns slack-user-gallery.image-test
  (:require [clojure.test :refer :all]
            [slack-user-gallery.image :refer [render-image]]))

(deftest with-simple-html
  (testing "returns correct size BufferedImage"
    (let [image (render-image "<html><body><div>test</div></body></html>")]
      (is (= 863 (.getWidth image)))
      (is (= 1000 (.getHeight image))))))

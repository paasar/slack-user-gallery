(ns slack-user-gallery.image
  (:require [clojure.java.io :refer [input-stream]])
  (:import (cz.vutbr.web.css MediaSpec)
           (org.fit.cssbox.css DOMAnalyzer CSSNorm)
           (org.fit.cssbox.io DefaultDOMSource StreamDocumentSource)
           (org.fit.cssbox.layout BrowserCanvas)
           (java.awt Dimension)
           (java.net URL)))

(defn render-image [html]
  (with-open [is (input-stream (.getBytes html))]
    (let [ds (StreamDocumentSource. is (URL. "file://dummy") "text/html")
          parser (DefaultDOMSource. ds)
          doc (.parse parser)
          dim (Dimension. 863 1000)
          media (doto (MediaSpec. "screen")
                      (.setDimensions (.width dim) (.height dim))
                      (.setDeviceDimensions (.width dim) (.height dim)))
          da (doto (DOMAnalyzer. doc (.getURL ds))
                   (.setMediaSpec media)
                   (.attributesToStyles)
                   (.addStyleSheet nil (CSSNorm/stdStyleSheet) org.fit.cssbox.css.DOMAnalyzer$Origin/AGENT)
                   (.addStyleSheet nil (CSSNorm/userStyleSheet) org.fit.cssbox.css.DOMAnalyzer$Origin/AGENT)
                   (.getStyleSheets))
          content-canvas (doto (BrowserCanvas. (.getRoot da) da (.getURL ds))
                               (.createLayout dim))]
      (.getImage content-canvas))))


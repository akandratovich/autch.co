(ns autch.api
  (:require [goog.net.XhrIo :as xhr]
            [goog.net.Jsonp :as jsonp]))

(defn- api-uri [endpoint] (goog.Uri. (str "https://api.twitch.tv/kraken" endpoint)))

(defn- error-callback [error]
  (.log js/console error)
  (js/toast "Failed to process request..." 2000)
  (aset js/location "hash" "/"))

(defn- retrieve [endpoint payload callback]
  (.send (goog.net.Jsonp. (api-uri endpoint))
         payload
         callback
         error-callback))

(defn games [callback]
  (retrieve "/games/top?limit=40" {} callback))

(defn game-streams [game callback]
  (retrieve (str "/streams?limit=40&game=" game) {} callback))

(defn streams [callback]
  (retrieve "/streams?limit=40" {} callback))

(defn stream [name callback]
  (retrieve (str "/streams?channel=" name) {} callback))

(ns autch.routes
  (:require [autch.core :as core]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType])
  (:import goog.History))

(secretary/set-config! :prefix "#")

(defroute "/" [] (core/root))

(defroute "/games" [] (core/games))

(defroute "/games/:name" [name] (core/game name))

(defroute "/streams" [] (core/streams))

(defroute "/streams/:name" [name] (core/stream name))

(defroute "*" []
  (set! (.-hash js/location) "/"))

(let [h (History.)]
  (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
  (doto h (.setEnabled true)))

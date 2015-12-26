(ns autch.core
  (:require [autch.api :as api]
            [goog.dom :as dom]
            [goog.object :as obj]
            [goog.style :as style]
            [goog.events :as events]
            [hipo.core :as hipo]))

(def play-state (atom false))

(defn- detect-audio-type []
  (let [player (dom/getElement "player")]
    (cond
      (= "probably" (.canPlayType player "audio/aac")) "aac"
      (= "probably" (.canPlayType player "audio/mpeg")) "mpeg"
      (= "maybe" (.canPlayType player "audio/aac")) "aac"
      (= "maybe" (.canPlayType player "audio/mpeg")) "mpeg"
      :else nil)))

(def audio-type (detect-audio-type))

(if (nil? audio-type)
  (.log js/console "unsupported media"))

(let [player (dom/getElement "player")
      source (dom/getElement "player-source")]
  (aset source "type" (str "audio/" audio-type)))

(defn- activate-audio [name]
  (let [player (dom/getElement "player")
        source (dom/getElement "player-source")
        image (dom/getElement "play")]
    (aset source "src" (str "//content.autch.co/" (.toLowerCase name) "." audio-type))
    (.load player)
    (.play player)
    (aset image "src" "img/pause-button.png")
    (reset! play-state true)))

(defn- deactivate-audio []
  (let [player (dom/getElement "player")
        source (dom/getElement "player-source")
        image (dom/getElement "play")]
    (.pause player)
    (aset source "src" "")
    (.load player)
    (if image
      (do
        (aset image "src" "img/play-button.png")
        (reset! play-state false)))))

(defn- toggle-audio [name]
  (if @play-state (deactivate-audio) (activate-audio name)))

(defn encode [data]
  (js/encodeURIComponent data))


(defn- close-sections []
  (deactivate-audio)
  (doseq [element (dom/getElementsByClass "layout-section")]
    (style/setStyle element "display" "none")))

(defn- open-section [name]
  (let [clazz (str "layout-section-" name)
        section (dom/getElementByClass clazz)
        inner (dom/getElementByClass "layout-section-inner" section)]
    (dom/removeChildren inner)
    (style/setStyle section "display" "block")))


(defn game-snippet [name channels viewers]
  [:a {:href (str "#/games/" (encode name))}
   [:div {:class "row row-item"}
    [:div {:class "col s10 truncate"} name]
    [:div {:class "col s2"} viewers]]])

(defn stream-snippet [id name status viewers]
  [:a {:href (str "#/streams/" (encode id))}
   [:div {:class "row row-item"}
    [:div {:class "col s3 truncate" :title name} name]
    [:div {:class "col s7 truncate" :title status} status]
    [:div {:class "col s2"} viewers]]])

(defn stream-one-snippet [name status viewers href]
  [:div {:class "row"}
   [:div {:class "col s2 stream-one-play"}
    [:img {:id "play" :src "img/play-button.png" :width 125}]]
   [:div {:class "col s9 offset-s1"}
    [:div {:class "row"}
     [:div {:class "col s8"} name]
     [:div {:class "col s2"} viewers]
     [:div {:class "col s2" :title "Open twitch stream"}
      [:a {:href href :target "_blank"}
       [:img {:src "img/twitch.png" :style "width: 50px; float: left;"}]]]]
    [:div {:class "row stream-one-status"}
     [:div {:class "col s12 truncate" :title status} status]]]])

(defn- render-game [name channels viewers]
  (let [element (hipo/create (game-snippet name channels viewers))
        section (dom/getElementByClass "layout-section-games")
        inner (dom/getElementByClass "layout-section-inner" section)]
    (dom/appendChild inner element)))

(defn- render-games [data]
  (open-section "games")
  (obj/forEach (.-top data)
    (fn [val key obj]
      (let [game (js->clj val)
            channels (get game "channels")
            viewers (get game "viewers")
            name (get-in game ["game" "name"])]
        (render-game name channels viewers)))))


(defn- render-stream [id name status viewers]
  (let [element (hipo/create (stream-snippet id name status viewers))
        section (dom/getElementByClass "layout-section-streams")
        inner (dom/getElementByClass "layout-section-inner" section)]
    (dom/appendChild inner element)))

(defn- render-streams [data]
  (open-section "streams")
  (obj/forEach (aget data "streams")
    (fn [val key obj]
      (let [stream (js->clj val)
            name (get-in stream ["channel" "display_name"])
            identifier (get-in stream ["channel" "name"])
            status (get-in stream ["channel" "status"])
            viewers (get stream "viewers")]
        (render-stream identifier name status viewers)))))

(defn- render-stream-one [name code status viewers href]
  (let [element (hipo/create (stream-one-snippet name status viewers href))
        section (dom/getElementByClass "layout-section-stream")
        inner (dom/getElementByClass "layout-section-inner" section)]
    (dom/appendChild inner element)
    (let [button (dom/getElement "play")]
      (events/listen button "click"
        (fn [event] (toggle-audio code))))))

(defn- render-stream-one-offline []
  (let [section (dom/getElementByClass "layout-section-stream")
        inner (dom/getElementByClass "layout-section-inner" section)
        offline (goog.dom.createDom "span")]
    (dom/appendChild inner offline)
    (dom/setTextContent offline "Offline")))

(defn- render-one-stream [data]
  (open-section "stream")
  (let [streams (get (js->clj data) "streams")]
    (if (empty? streams)
      (render-stream-one-offline)
      (let [stream (first streams)
            name (get-in stream ["channel" "display_name"])
            code (get-in stream ["channel" "name"])
            href (get-in stream ["channel" "url"])
            status (get-in stream ["channel" "status"])
            viewers (get stream "viewers")]
        (render-stream-one name code status viewers href)))))


(defn root []
  (close-sections)
  (open-section "root"))

(defn games []
  (close-sections)
  (api/games render-games))

(defn game [name]
  (close-sections)
  (api/game-streams (encode name) render-streams))

(defn streams []
  (close-sections)
  (api/streams render-streams))

(defn stream [name]
  (close-sections)
  (api/stream name render-one-stream))

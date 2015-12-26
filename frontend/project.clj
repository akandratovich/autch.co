(defproject autch "0.1.0-SNAPSHOT"
  :description "Twitch audio player"
  :url "https://autch.co/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-beta3"]
                 [org.clojure/clojurescript "0.0-3269"]
                 [secretary "1.2.3"]
                 [hipo "0.4.0"]]
  :plugins [[lein-cljsbuild "1.0.6"]]

  :cljsbuild {
    :builds [
      {
        :id "dev"
        :source-paths ["src/cljs"]
        :compiler {
          :output-to "resources/web/js/gen/dev/autch-main.js"
          :output-dir "resources/web/js/gen/dev"
          :optimizations :none
          :source-map true
          :pretty-print true
        }
      }
      {
        :id "prod"
        :source-paths ["src/cljs"]
        :compiler {
          :output-to "resources/web/js/gen/prod/autch-main.js"
          :output-dir "resources/web/js/gen/prod"
          :optimizations :advanced
          :pretty-print false
        }
      }
    ]
  }

  :source-paths ["src/clj"]
  :main autch.core

  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
)
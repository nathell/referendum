(ns referendum.core
  (:require [integrant.core :as integrant]
            [hikari-cp.core :as pool]
            [referendum.handler :as handler]
            [ring.adapter.jetty :as jetty]))

(def config {:http/server {:port 8009, :db (integrant/ref :db/pool)}
             :db/pool {:jdbc-url "jdbc:sqlite:referendum.sqlite"}})

(defmethod integrant/init-key :http/server [_ {:keys [port db]}]
  (jetty/run-jetty (handler/make-handler db) {:port port, :join? false}))

(defmethod integrant/halt-key! :http/server [_ server]
  (.stop server))

(defmethod integrant/init-key :db/pool [_ opts]
  (pool/make-datasource opts))

(defmethod integrant/halt-key! :db/pool [_ pool]
  (pool/close-datasource pool))

(defonce system
  (integrant/init config))

(defn run [opts]
  system)

(ns referendum.core
  (:require [integrant.core :as integrant]
            [hikari-cp.core :as pool]
            [referendum.handler :as handler]
            [ring.adapter.jetty :as jetty]))

(def config {:server {:port 8009}
             :db {:jdbc-url "jdbc:sqlite:referendum.sqlite"}})

(defmethod integrant/init-key :server [_ {:keys [port]}]
  (jetty/run-jetty #'handler/handler {:port port, :join? false}))

(defmethod integrant/halt-key! :server [_ server]
  (.stop server))

(defmethod integrant/init-key :db [_ opts]
  (pool/make-datasource opts))

(defmethod integrant/halt-key! :db [_ pool]
  (pool/close-datasource pool))

(defonce system
  (integrant/init config))

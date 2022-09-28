(ns referendum.handler
  (:require [clojure.string :as string]
            [hiccup2.core :refer [html]]
            [next.jdbc :as jdbc]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]))

(defn layout [& content]
  (str
   "<!DOCTYPE html>\n"
   (html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
      [:link {:rel "stylesheet" :href "style.css"}]
      [:title "referendum"]]
     (into
      [:body]
      content)])))

(defn handle-referendum [db]
  (with-open [c (jdbc/get-connection db)]
    (let [referendum (jdbc/execute-one! c ["select * from referendum"])]
      (layout
       [:div
        [:h1 "Czy jesteś za " (:referendum/topic referendum) "?"]]))))

(defn html-response [body]
  {:status 200, :headers {"content-type" "text/html; charset=utf-8"}, :body body})

(defn basic-handler [{:keys [request-method uri db] :as request}]
  (condp = [request-method uri]
    [:get "/"] (html-response (handle-referendum db))
    ;; [:get "/faq"] (html-response (faq))
    ;; [:post "/"] (handle-guess request)
    {:status 404,
     :headers {"Content-Type" "text/plain; charset=utf-8"},
     :body "żodeen"}))

(defn wrap-db [handler db]
  (fn [request]
    (handler (assoc request :db db))))

(defn make-handler [db]
  (-> #'basic-handler
      (wrap-db db)
      (wrap-params)
      (wrap-resource "/")
      (wrap-content-type)))

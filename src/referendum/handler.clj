(ns referendum.handler
  (:require [clojure.string :as string]
            [hiccup2.core :refer [html]]
            [next.jdbc :as jdbc]
            [reitit.ring :as ring]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]))

(defn html-response [body]
  {:status 200, :headers {"content-type" "text/html; charset=utf-8"}, :body body})

(defn layout [& content]
  (html-response
   (str
    "<!DOCTYPE html>\n"
    (html
     [:html
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
       [:title "referendum"]]
      (into
       [:body]
       content)]))))

(defn handle-index [req]
  (layout
   [:h1 "Hello, world"]))

(defn result [from to]
  (+ from (* (rand) (- to from))))

(defn enrich [{:referendum/keys [result_from result_to] :as referendum}]
  (assoc referendum :referendum/result (result result_from result_to)))

(defn get-referendum [conn id]
  (some-> (jdbc/execute-one! conn ["select * from referendum where id = ?" id])
          (enrich)))

(defn handle-referendum [{:keys [db path-params] :as req}]
  (with-open [c (jdbc/get-connection db)]
    (let [referendum (get-referendum c (:id path-params))]
      (layout
       (if referendum
         [:div
          [:h1 "Czy jesteś za " (:referendum/topic referendum) "?"]
          [:h1.result (format "%.2f%%" (:referendum/result referendum))]]
         [:h1 "nie ma takiego numeru"])))))

(def basic-handler
  (ring/ring-handler
   (ring/router
    [["/" {:get handle-index}]
     ["/referendum/:id" {:get handle-referendum}]])
   (constantly
    {:status 404,
     :headers {"Content-Type" "text/plain; charset=utf-8"},
     :body "żodyn"})))

(defn wrap-db [handler db]
  (fn [request]
    (handler (assoc request :db db))))

(defn make-handler [db]
  (-> #'basic-handler
      (wrap-db db)
      (wrap-params)
      (wrap-resource "/")
      (wrap-content-type)))

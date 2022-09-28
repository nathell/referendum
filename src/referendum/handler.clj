(ns referendum.handler
  (:require [clojure.string :as string]
            [hiccup2.core :refer [html]]
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

(defn basic-handler [{:keys [request-method uri] :as request}]
  (condp = [request-method uri]
    ;; [:get "/"] (html-response (page))
    ;; [:get "/faq"] (html-response (faq))
    ;; [:post "/"] (handle-guess request)
    {:status 404,
     :headers {"Content-Type" "text/plain; charset=utf-8"},
     :body "żodyn"}))

(def handler
  (-> basic-handler
      (wrap-params)
      (wrap-resource "/")
      (wrap-content-type)))

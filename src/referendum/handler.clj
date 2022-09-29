(ns referendum.handler
  (:require [clojure.set :as set]
            [hiccup2.core :refer [html]]
            [next.jdbc :as jdbc]
            [reitit.ring :as ring]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :as response]))

(def domain "voteputinstyle.online")

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
       [:link {:rel "stylesheet" :type "text/css" :href "https://cdn.jsdelivr.net/npm/water.css@2/out/water.css"}]
       [:link {:rel "stylesheet" :type "text/css" :href "/assets/style.css"}]
       [:title "Vote Putin style"]
       [:meta {:name "description" :content "Skoro Putin może robić referenda z dupy, to Ty też możesz!"}]
       [:meta {:property "og:type" :content "website"}]
       [:meta {:property "og:image" :content "https://voteputinstyle.online/assets/slava-ukraini.jpg"}]
       [:meta {:name "twitter:card" :content "summary_large_image"}]
       [:meta {:property "twitter:domain" :content "voteputinstyle.online"}]]
      (into
       [:body]
       content)]))))

(defn result [from to]
  (+ from (* (rand) (- to from))))

(defn enrich [{:referendum/keys [result_from result_to] :as referendum}]
  (assoc referendum
         :referendum/result (result result_from result_to)
         :referendum/turnout (+ 70 (* 25 (rand)))))

(defn get-referendum [conn id]
  (some-> (jdbc/execute-one! conn ["select *, datetime() between started_on and running_until as active from referendum where id = ?" id])
          (set/rename-keys {:active :referendum/active})
          (enrich)))

(defn get-latest-referendums [conn]
  (jdbc/execute! conn ["select id, topic from referendum order by started_on desc limit 100"]))

(defn localized-question [{:referendum/keys [topic locale]}]
  (str "Czy jesteś za " topic "?"))

(defn referendum-url [{:referendum/keys [id]}]
  (str "https://" domain "/referendum/" id))

(defn handle-referendum [{:keys [db path-params] :as req}]
  (with-open [c (jdbc/get-connection db)]
    (let [referendum (get-referendum c (:id path-params))]
      (layout
       (if referendum
         [:div.referendum
          [:img {:src "/assets/referendum.webp"}]
          [:h2 (localized-question referendum)]
          (if (:referendum/active referendum)
            [:div.voting
             [:a.button.yes {:href "/vote"} [:button "TAK"]]
             [:a.button.no {:href "/vote"} [:button "NIE"]]]
            [:h3 "to referendum już się zakończyło :("])
          [:h1.result (format "%.2f%%" (:referendum/result referendum))]
          [:h1.za "za"]
          [:p.turnout "frekwencja: " [:b (format "%.2f%%" (:referendum/turnout referendum))]]
          (let [link (referendum-url referendum)]
            [:p.link "link do tego referendum: " [:a {:href link} link]])]
         [:h1 "nie ma takiego numeru"])))))

(defn handle-vote [_]
  (layout
   [:div.referendum
    [:img {:src "/assets/referendum.webp"}]
    [:h1 "dziękujemy za Twój głos za!"]
    [:p [:a {:href "/"} "wróć do listy referendów"]]]))

(defn handle-index [{:keys [db] :as req}]
  (with-open [c (jdbc/get-connection db)]
    (let [referendums (get-latest-referendums c)]
      (layout
       [:div.content
        [:h1.title "voteputinstyle.online"]
        [:h2.headline "jeśli Putin może, to Ty też!"]
        [:a {:href "/new"} "nowe referendum"]
        [:h1 "ostatnie referenda"]
        (into [:ul.referendums]
              (map (fn [{:referendum/keys [id] :as referendum}]
                     [:li [:a {:href (str "/referendum/" id)}
                           (localized-question referendum)]]))
              referendums)
        [:img {:src "/assets/slava-ukraini.jpg"}]]))))

(defn parse-int [s]
  (try (Integer/parseInt s)
       (catch Exception e nil)))

(defn remove-nils [m]
  (into {}
        (filter val)
        m))

(defn validate [{:strs [topic days result_from result_to]}]
  (let [[days-int from-int to-int] (map parse-int [days result_from result_to])]
    {:validation
     (remove-nils
      {:topic (cond (not (string? topic)) "musisz podać pytanie referendalne!"
                    (< (count topic) 10) "za krótkie pytanie (min. 10 znaków)"
                    (> (count topic) 500) "za długie pytanie (maks. 500 znaków)")
       :days (cond (not (string? days)) "musisz podać czas trwania referendum!"
                   (not days-int) "czas trwania musi być liczbą dodatnią"
                   (< days-int 1) "za krótkie referendum (min. 1 dzień)"
                   (> days-int 14) "za długie referendum (maks. 2 tygodnie)")
       :result (cond (or (not (string? result_from)) (not (string? result_to))) "musisz podać zakres wyników jak na Putina przystało"
                     (or (not from-int) (not to-int)) "zakres wyników musi być parą liczb całkowitych"
                     (not (<= from-int to-int)) "wynik od nie może być większy od wyniku do"
                     (< from-int 70) "referendum za bardzo demokratyczne")})
     :input
     {:topic topic, :days days-int, :result_from from-int, :result_to to-int}}))

(defn valid-message [validation k]
  (when-let [msg (get validation k)]
    [:p.error msg]))

(defn create-referendum! [conn {:keys [topic days result_from result_to]}]
  (let [days-inc (str "+" days " days")]
    (jdbc/execute-one!
     conn
     ["insert into referendum (topic, locale, started_on, running_until, result_from, result_to) values (?, ?, datetime(), datetime('now', ?), ?, ?)"
      topic "pl" days-inc result_from result_to]
     {:return-keys true})))

(defn handle-new [{:keys [db request-method form-params]}]
  (let [{:keys [validation input]} (when (= request-method :post)
                                     (validate form-params))]
    (if (= validation {})
      (with-open [c (jdbc/get-connection db)]
        (let [res (create-referendum! c input)
              id (-> res first val)]
          (response/redirect (str "/referendum/" id) :see-other)))
      (layout
       [:div.content
        [:h1 "nowe referendum"]
        [:form {:method "POST" :action "/new"}
         [:div
          [:fieldset
           [:label.fixed {:for "topic"} "Czy jesteś za"]
           [:input {:type "text" :name "topic" :value (:topic input) :placeholder "treścią pytania referendalnego w narzędniku (za kim, za czym?)"}]
           "?"]
          (valid-message validation :topic)]
         [:div
          [:fieldset
           [:label.fixed {:for "days"} "Dni trwania"]
           [:input {:type "number" :name "days" :value (:days input)}]]
          (valid-message validation :days)]
         [:div
          [:fieldset
           [:label.fixed {:for "result_from"} "Wynik od"]
           [:input {:type "number" :name "result_from" :value (:result_from input)}]
           "%"
           [:label {:for "result_to"} "do"]
           [:input {:type "number" :name "result_to" :value (:result_to input)}]
           "%"]
          (valid-message validation :result)]
         [:h2 "umowa dżentelmeńska"]
         [:p "zanim stworzysz referendum, "
          [:a {:href "https://armysos.com.ua/en/help-the-army"} "wrzuć na zbiórkę dla armii"]
          " albo "
          [:a {:href "https://zrzutka.pl/w6p3cg"} "na zrzutkę humanitarną"]
          " "
          [:a {:href "https://www.facebook.com/profile.php?id=100013477777304"} "Archiego"]
          ". choćby złotówkę."]
         [:p "umawiamy się, że bez wpłaty nie robimy referendów, dobra?"]
         [:button "dorzucone – stwórz referendum"]]]))))

(defn handle-create [req]
  (layout
   [:h1 "done"]))

(def basic-handler
  (ring/ring-handler
   (ring/router
    [["/" {:get handle-index}]
     ["/new" {:get handle-new, :post handle-new}]
     ["/vote" {:get handle-vote}]
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

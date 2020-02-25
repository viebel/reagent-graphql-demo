(ns apollo-example.core
  (:require [helix.hooks :as hooks]
            [reagent.core :as r]
            [clojure.string]
            [graphql-query.core :refer [graphql-query]]
            ["react" :as react]
            ["apollo-client" :refer [ApolloClient]]
            ["graphql-tag" :default gql]
            ["apollo-link-http" :refer [HttpLink]]
            ["apollo-cache-inmemory" :refer [InMemoryCache]]
            ["@apollo/react-hooks" :as apollo :refer [ApolloProvider useQuery useMutation]]
            [cljs-bean.core :as b :refer [bean ->clj ->js]]))



(def client (ApolloClient. #js {:link (HttpLink. #js {:uri "http://localhost:8888/graphql"})
                                :cache (InMemoryCache.  )
                                :name "apollo-cljs-Doc"
                                :queryDeduplication false
                                :defaultOptions (->js {:watchQuery {:fetchPolicy "cache-and-network"}})
                                :connectToDevTools true}))

(comment
  (.watchQuery client #js {:query my-rating})
  (def q (.query client #js {:query game-and-my-rating-query
                             ;;          :fetchPolicy "no-cache"
                             :variables #js {:id "1234"}}))
  (.then q println)
  (def w (.watchQuery client #js {:query game-and-my-rating-query
                                        ;                 :fetchPolicy "no-cache"
                                  :variables #js {:id "1234"}
                                  :notifyOnNetworkStatusChange false
                                  :pollInterval 1000}))
  (.-variables w)
  (.stopPolling w)
  (.refetch w #js {:id "1234"})
  (.subscribe w #js {:next #(println "bbb" (.-loading %) %)})
  (-> (.result w)
      (.then println)))

(def all-games-query (gql (graphql-query {:queries [[:games [:id :summary :name]]]})))
(def game-query (gql
                 (graphql-query {:operation {:operation/type :query
                                             :operation/name :Game}
                                 :variables [{:variable/name :$id
                                              :variable/type :ID!}]
                                 :queries [[:game_by_id {:id :$id} [:name :id :summary [:rating_summary [:average]]]]]})))
(def game-and-my-rating-query (gql (graphql-query {:operation {:operation/type :query

                                                               :operation/name :Game}
                                                   :variables [{:variable/name :$id
                                                                :variable/type :ID!}]
                                                   :queries [[:game_by_id {:id :$id} [:name
                                                                                      :id
                                                                                      :summary
                                                                                      [:rating_summary [:average]]]]
                                                             [:game_rating {:game_id :$id
                                                                            :member_id "37"} [:rating
                                                                                              [:game [:name]]]]]}
                                                  )))
(def edit-game-query (gql (graphql-query {:operation {:operation/type :mutation
                                                      :operation/name :EditGameSummary}
                                          :variables [{:variable/name :$id
                                                       :variable/type :ID!}
                                                      {:variable/name :$summary
                                                       :variable/type :String!}]
                                          :queries [[:edit_game {:id :$id
                                                                 :summary :$summary} [:summary
                                                                                      :id]]]})))
(def my-rating (gql (graphql-query {:operation {:operation/type :query
                                                :operation/name :MyRating}
                                    :variables [{:variable/name :$id
                                                 :variable/type :ID!}]
                                    :queries [{:query/data [:game_rating {:game_id :$id
                                                                          :member_id "37"}
                                                            [:meta/typename
                                                             :rating
                                                             [:game [ {:field/data [:name]
                                                                       :field/alias :name}]]]]
                                               :query/alias :game_rating}]})))
(def rate-game-query (gql (graphql-query {:operation {:operation/type :mutation
                                                      :operation/name :RateGame}
                                          :variables [{:variable/name :$game_id
                                                       :variable/type :ID!}
                                                      {:variable/name :$rating
                                                       :variable/type :Int!}]
                                          :queries [[:rate-game {:game-id :$game_id
                                                                 :member-id "37"
                                                                 :rating :$rating}
                                                     [:name :id]]]}
                                         {:kw->gql-name #(clojure.string/replace (name %) #"-" "_")})))


(defn EditGameButton [props]
  (let [{:keys [id summary]} (bean props)
        [state set-state] (hooks/use-state {:summary summary})
        [edit-game {:keys [loading error data]}] (-> (useMutation edit-game-query #_(->js {:update (fn [cache _]
                                                                                                     (.writeQuery cache (->js {:query all-games-query
                                                                                                                               :data {:games []}})))}))
                                                     (->clj))]
    (r/as-element
     [:span
      [:textarea {:rows "1"
                  :value (str (:summary state))
                  :on-change (fn [event] (set-state {:summary (.-value (.-target event))}))}]
      [:button {:on-click #(edit-game (->js {:variables {:id id :summary (:summary state)}}))}
       "Edit"]])))

(defn RateGameButton [props]
  (let [{:keys [id rating]} (bean props)
        [state set-state] (hooks/use-state {:rating rating})
        [rate-game {:keys [loading error data]}] (-> (useMutation rate-game-query (->js {:refetchQueries [{:query game-and-my-rating-query
                                                                                                           :variables {:id id}}]}))
                                                     (->clj))]
    (r/as-element
     [:span
      [:textarea {:rows "1"
                  :value (str (:rating state))
                  :on-change (fn [event] (set-state {:rating  (.-value (.-target event))}))}]
      [:button {:on-click #(rate-game (->js {:variables {:game_id id :rating (int (:rating state))}}))}
       "Rate"]
      (when error
        [:span (str "error while rating: ") (str error)])])))

(defn GameWithVar [props]
  (let [{set-state :setState id :id} (bean props)
        {:keys [loading error data]} (-> (useQuery game-and-my-rating-query (->js {:variables {:id id}}))
                                         (bean :recursive true))
        {{:keys [rating]} :game_rating}  data]
    (r/as-element
     (cond
       loading [:div "Loading games..."]
       error [:div "Error :("]
       :else (let [{:keys [name id summary rating_summary]} (:game_by_id data)]
               [:div [:button {:on-click #(set-state {:view :games})} "Back"]
                [:div (str "id: " id)]
                [:div (str "name: " name)]
                [:div (str "summary: " summary)]
                [:div (str "my rating " rating)]
                [:div (str "average rating: " (:average rating_summary))]
                [:div [:> EditGameButton {:id id :summary summary}]]
                [:div [:> RateGameButton {:id id :rating rating}]]])))))

(defn Games [props]
  (let [{set-state :setState} (bean props)
        {:keys [loading error data]} (-> (useQuery all-games-query)
                                         (bean :recursive true))]
    (r/as-element
     (cond
       loading [:div "Loading..."]
       error [:div "Error :("]
       :else [:div
              [:div (for [{:keys [name id summary]} (:games data)]
                      [:div {:key id
                             :on-click #(set-state {:view :game
                                                    :id id})}
                       [:span "Game "]
                       [:span (str id)]
                       [:span " "]
                       [:span (str "named " name)]
                       [:span (str "  summary: "  summary)]])]]))))

(defn Container [_]
  (let [[state set-state] (hooks/use-state {:view :games})]
    (r/as-element
     (case (:view state)
       :games [:div [:> Games {:set-state set-state}]]
       :game [:div [:> GameWithVar {:set-state set-state
                                    :id (:id state)}]]))))

(defn App [_]
  [:> ApolloProvider {:client client}
   [:> Container]])

(defn ^:dev/after-load start []
  (r/render [App] (.getElementById js/document "app")))

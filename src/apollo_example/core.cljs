(ns apollo-example.core
  (:require [helix.hooks :as hooks]
            [reagent.core :as r]
            ["react" :as react]
            ["apollo-boost" :default ApolloClient :refer [gql]]
            ["@apollo/react-hooks" :as apollo :refer [ApolloProvider useQuery useMutation]]
            [cljs-bean.core :as b :refer [bean ->clj ->js]]))

(def client (ApolloClient. #js {:uri "http://localhost:8888/graphql"}))


(def all-games-query (gql "{games {id summary name}}"))

(def game-query (gql "query Game($id: ID!)
  {
  game_by_id(id: $id) {
    name
    id
    summary
    rating_summary {
average
}
  }
}"))
(def game-and-my-rating-query (gql "query Game($id: ID!)
  {
  game_by_id(id: $id) {
    name
    id
    summary
    rating_summary {
average
}
  }
game_rating(game_id: $id, member_id: \"37\") {
    rating
    game {
      name
    }
  }
}"))

(def edit-game-query (gql "mutation EditGameSummary($id: ID!, $summary: String!) {
    edit_game(id: $id, summary: $summary) {
       summary
       id
}
}"))

(def my-rating (gql "query MyRating($id: ID!) {
 game_rating(game_id: $id, member_id: \"37\") {
    rating
    game {
      name
    }
  }
}"))

(def rate-game-query (gql "mutation RateGameQuery($game_id: ID, $rating: Int!) {
    rate_game(game_id: $game_id, member_id: \"37\", rating: $rating) {
name
id
}
}"))

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

(comment
  (r/as-element [:div "aa" [:div "bb" [:div "x"]]]))

(defn ^:dev/after-load start []
  (r/render [App] (.getElementById js/document "app")))

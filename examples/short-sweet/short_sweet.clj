(ns short-sweet
  (:require [reifyhealth.specmonstah.core :as sm]
            [reifyhealth.specmonstah.spec-gen :as sg]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;;-------*****--------
;; Begin example setup
;;-------*****--------

;; ---
;; Define specs for our domain entities

;; The ::id should be a positive int, and to generate it we increment
;; the number stored in `id-seq`. This ensures unique ids and produces
;; values that are easier for humans to understand
(def id-seq (atom 0))
(s/def ::id (s/with-gen pos-int? #(gen/fmap (fn [_] (swap! id-seq inc)) (gen/return nil))))
(s/def ::not-empty-string (s/and string? not-empty #(< (count %) 10)))

(s/def ::username ::not-empty-string)
(s/def ::user (s/keys :req-un [::id ::username]))

(s/def ::created-by-id ::id)
(s/def ::content ::not-empty-string)
(s/def ::post (s/keys :req-un [::id ::created-by-id ::content]))

(s/def ::post-id ::id)
(s/def ::like (s/keys :req-un [::id ::post-id ::created-by-id]))

;; ---
;; The schema defines specmonstah `ent-types`, which roughly
;; correspond to db tables. It also defines the `:spec` for generating
;; ents of that type, and defines ent `relations` that specify how
;; ents reference each other
(def schema
  {:user {:prefix :u
          :spec   ::user}
   :post {:prefix    :p
          :spec      ::post
          :relations {:created-by-id [:user :id]}}
   :like {:prefix      :l
          :spec        ::like
          :relations   {:post-id       [:post :id]
                        :created-by-id [:user :id]}
          :constraints {:created-by-id #{:uniq}}}})

;; Our "db" is a vector of inserted records we can use to show that
;; entities are inserted in the correct order
(def mock-db (atom []))

(defn insert*
  "Simulates inserting records in a db by conjing values onto an
  atom. ent-tye is `:user`, `:post`, or `:like`, corresponding to the
  keys in the schema. `spec-gen` is the map generated by clojure.spec"
  [{:keys [data] :as db} {:keys [ent-type spec-gen]}]
  (swap! mock-db conj [ent-type spec-gen]))

(defn insert [query]
  (reset! id-seq 0)
  (reset! mock-db [])
  (-> (sg/ent-db-spec-gen {:schema schema} query)
      (sm/visit-ents-once :inserted-data insert*))
  ;; normally you'd return the expression above, but return nil for
  ;; the example to not produce overwhelming output
  nil)


;;-------*****--------
;; Begin snippets to try in REPL
;;-------*****--------

;; Return a map of user entities and their spec-generated data
(-> (sg/ent-db-spec-gen {:schema schema} {:user [[3]]})
    (sm/attr-map :spec-gen))

;; You can specify a username and id
(-> (sg/ent-db-spec-gen {:schema schema} {:user [[1 {:spec-gen {:username "Meeghan"
                                                                :id       100}}]]})
    (sm/attr-map :spec-gen))

;; Generating a post generates the user the post belongs to, with
;; foreign keys correct
(-> (sg/ent-db-spec-gen {:schema schema} {:post [[1]]})
    (sm/attr-map :spec-gen))

;; Generating a like also generates a post and user
(-> (sg/ent-db-spec-gen {:schema schema} {:like [[1]]})
    (sm/attr-map :spec-gen))


;; The `insert` function shows that records are inserted into the
;; simulate "database" (`mock-db`) in correct dependency order:
(insert {:like [[1]]})
@mock-db

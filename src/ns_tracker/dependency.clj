(ns ns-tracker.dependency
  "Bidirectional graphs of dependencies and dependent objects."
  (:require [clojure.set :refer [union]]))

(defn graph "Returns a new, empty, dependency graph." []
  {:dependencies {}
   :dependents {}})

(defn seq-union
  "A union that preserves order."
  ([] '())
  ([s1] s1)
  ([s1 s2] (concat s1 (remove (set s1) s2)))
  ([s1 s2 & sets] (reduce seq-union (list* s1 s2 sets))))

(defn- transitive
  "Recursively expands the set of dependency relationships starting
  at (get m x)"
  [m x]
  (loop [acc [], deps (get m x)]
    (if-let [new-deps (seq (distinct (remove (set acc) deps)))]
      (recur
       (into acc new-deps)
       (mapcat (partial get m) new-deps))
      acc)))

(defn dependents-1
  "Returns the set of all things which depend upon x, only directly."
  [graph x]
  (get (:dependents graph) x))

(defn dependencies
  "Returns the set of all things x depends on, directly or transitively."
  [graph x]
  (transitive (:dependencies graph) x))

(defn dependents
  "Returns the set of all things which depend upon x, directly or
  transitively."
  [graph x]
  (transitive (:dependents graph) x))

(defn depends?
  "True if x is directly or transitively dependent on y."
  [graph x y]
  (some #(= y %) (dependencies graph x)))

(defn dependent
  "True if y is a dependent of x."
  [graph x y]
  (some #(= y %) (dependents graph x)))

(defn close-dependents
  [graph x]
  (let [dependents (dependents-1 graph x)]
    (if (= 1 (count dependents))
      dependents
      (->> dependents
           (remove (fn [ns]
                     (some #(depends? graph ns %) (disj dependents ns))))
           (set)))))

(defn- add-relationship [graph key x y]
  (update-in graph [key x] union #{y}))

(defn depend
  "Adds to the dependency graph that x depends on deps. Forbids
  circular and self-referential dependencies."
  ([graph x] graph)
  ([graph x dep]
   (assert (not (depends? graph dep x)) "circular dependency")
   (assert (not (= x dep)) "self-referential dependency")
   (-> graph
       (add-relationship :dependencies x dep)
       (add-relationship :dependents dep x)))
  ([graph x dep & more]
   (reduce (fn [g d] (depend g x d))
           graph (cons dep more))))

(defn- remove-from-map [amap x]
  (reduce (fn [m [k vs]]
            (assoc m k (disj vs x)))
          {} (dissoc amap x)))

(defn remove-all
  "Removes all references to x in the dependency graph."
  ([graph] graph)
  ([graph x]
   (assoc graph
     :dependencies (remove-from-map (:dependencies graph) x)
     :dependents (remove-from-map (:dependents graph) x)))
  ([graph x & more]
   (reduce remove-all
           graph (cons x more))))

(defn remove-key
  "Removes the key x from the dependency graph without removing x as a
  depedency of other keys."
  ([graph] graph)
  ([graph x]
   (assoc graph
     :dependencies (dissoc (:dependencies graph) x)))
  ([graph x & more]
   (reduce remove-key
           graph (cons x more))))

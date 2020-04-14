(ns crux.calcite-test
  (:require [clojure.test :as t]
            [crux.db :as db]
            [crux.fixtures :as f]
            [crux.fixtures.api :as fapi :refer [*api*]]
            [crux.fixtures.calcite :as cf :refer [query]]
            [crux.fixtures.kv :as kvf]
            [crux.fixtures.standalone :as fs]))

(defn- with-each-connection-type [f]
  (cf/with-calcite-connection f)
  (t/testing "With Avatica Connection"
    (cf/with-avatica-connection f)))

(t/use-fixtures :each fs/with-standalone-node cf/with-calcite-module kvf/with-kv-dir fapi/with-node with-each-connection-type)

(t/deftest test-sql-query
  (f/transact! *api* [{:crux.db/id :crux.sql.schema/person
                       :crux.sql.table/name "person"
                       :crux.sql.table/query '{:find [?id ?name ?homeworld ?alive]
                                               :where [[?id :name ?name]
                                                       [?id :homeworld ?homeworld]
                                                       [?id :alive ?alive]]}
                       :crux.sql.table/columns {'?id :keyword, '?name :varchar, '?homeworld :varchar, '?alive :boolean}}])

  (f/transact! *api* (f/people [{:crux.db/id :ivan :name "Ivan" :homeworld "Earth" :alive true}
                                {:crux.db/id :malcolm :name "Malcolm" :homeworld "Mars" :alive false}]))

  (t/testing "order by"
    (t/is (= [{:name "Ivan"}
              {:name "Malcolm"}]
             (query "SELECT PERSON.NAME FROM PERSON ORDER BY NAME ASC")))
    (t/is (= [{:name "Malcolm"}
              {:name "Ivan"}]
             (query "SELECT PERSON.NAME FROM PERSON ORDER BY NAME DESC"))))

  (t/testing "retrieve data"
    (t/is (= [{:name "Ivan"}
              {:name "Malcolm"}]
             (query "SELECT PERSON.NAME FROM PERSON")))

    (t/testing "retrieve data case insensitivity of table schema"
      (t/is (= [{:name "Ivan"}
                {:name "Malcolm"}]
               (query "select person.name from person")))))

  (t/testing "multiple columns"
    (t/is (= [{:name "Ivan" :homeworld "Earth"}
              {:name "Malcolm" :homeworld "Mars"}]
             (query "SELECT PERSON.NAME,PERSON.HOMEWORLD FROM PERSON"))))

  (t/testing "wildcard columns"
    (t/is (= #{{:name "Ivan" :homeworld "Earth" :id ":ivan" :alive true}
               {:name "Malcolm" :homeworld "Mars" :id ":malcolm" :alive false}}
             (set (query "SELECT * FROM PERSON")))))

  (t/testing "equals operand"
    (t/is (= #{{:name "Ivan"}}
             (set (query "SELECT NAME FROM PERSON WHERE NAME = 'Ivan'"))))
    (t/is (= #{{:name "Malcolm"}}
             (set (query "SELECT NAME FROM PERSON WHERE NAME <> 'Ivan'"))))
    (t/is (= #{{:name "Ivan"}}
             (set (query "SELECT NAME FROM PERSON WHERE 'Ivan' = NAME")))))

  (t/testing "in operand"
    (t/is (= #{{:name "Ivan"}}
             (set (query "SELECT NAME FROM PERSON WHERE NAME in ('Ivan')")))))

  (t/testing "and"
    (t/is (= #{{:name "Ivan"}}
             (set (query "SELECT NAME FROM PERSON WHERE NAME = 'Ivan' AND HOMEWORLD = 'Earth'")))))

  (t/testing "or"
    (t/is (= #{{:name "Ivan"} {:name "Malcolm"}}
             (set (query "SELECT NAME FROM PERSON WHERE NAME = 'Ivan' OR NAME = 'Malcolm'")))))

  (t/testing "boolean"
    (t/is (= #{{:name "Ivan"}}
             (set (query "SELECT NAME FROM PERSON WHERE ALIVE = TRUE"))))
    (t/is (= #{{:name "Malcolm"}}
             (set (query "SELECT NAME FROM PERSON WHERE ALIVE = FALSE")))))

  (t/testing "like"
    (t/is (= #{{:name "Ivan"}}
             (set (query "SELECT NAME FROM PERSON WHERE NAME LIKE 'Iva%'"))))
    (t/is (= #{{:name "Ivan"} {:name "Malcolm"}}
             (set (query "SELECT NAME FROM PERSON WHERE NAME LIKE 'Iva%' OR NAME LIKE 'Mal%'")))))

  (t/testing "arbitrary sql function"
    (t/is (= #{{:name "Iva"}}
             (set (query "SELECT SUBSTRING(NAME,1,3) AS NAME FROM PERSON WHERE NAME = 'Ivan'")))))

  (t/testing "unknown column"
    (t/is (thrown-with-msg? java.sql.SQLException #"Column 'NOCNOLUMN' not found in any table"
                            (query "SELECT NOCNOLUMN FROM PERSON")))))

(t/deftest test-namespaced-keywords
  (f/transact! *api* [{:crux.db/id :crux.sql.schema/person
                       :crux.sql.table/name "person"
                       :crux.sql.table/query '{:find [?id ?name ?homeworld ?alive]
                                               :where [[?id :name ?name]
                                                       [?id :homeworld ?homeworld]
                                                       [?id :alive ?alive]]}
                       :crux.sql.table/columns '{?id :keyword, ?name :varchar, ?homeworld :varchar, ?alive :boolean}}])
  (t/testing "namespaced keywords"
    (f/transact! *api* (f/people [{:crux.db/id :human/ivan :name "Ivan" :homeworld "Earth" :alive true}]))
    (t/is (= [{:id ":human/ivan", :name "Ivan"}] (query "SELECT ID,NAME FROM PERSON WHERE ID = CRUXID('human/ivan')")))))

(t/deftest test-numeric-columns
  (f/transact! *api* [{:crux.db/id :crux.sql.schema/person
                       :crux.sql.table/name "person"
                       :crux.sql.table/query {:find ['id 'name 'age]
                                              :where [['id :name 'name]
                                                      ['id :age 'age]]}
                       :crux.sql.table/columns {'id :keyword, 'name :varchar, 'age :long}}])
  (f/transact! *api* (f/people [{:crux.db/id :ivan :name "Ivan" :age 21}
                                {:crux.db/id :malcolm :name "Malcolm" :age 25}]))

  (t/is (= [{:name "Ivan" :age 21}]
           (query "SELECT PERSON.NAME,PERSON.AGE FROM PERSON WHERE AGE = 21")))

  (t/testing "Range"
    (t/is (= ["Malcolm"]
             (map :name (query "SELECT PERSON.NAME,PERSON.AGE FROM PERSON WHERE AGE > 21"))))
    (t/is (= ["Ivan"]
             (map :name (query "SELECT PERSON.NAME,PERSON.AGE FROM PERSON WHERE 23 > AGE"))))
    (t/is (= #{"Ivan" "Malcolm"}
             (set (map :name (query "SELECT PERSON.NAME,PERSON.AGE FROM PERSON WHERE AGE >= 21")))))
    (t/is (= ["Ivan"]
             (map :name (query "SELECT PERSON.NAME,PERSON.AGE FROM PERSON WHERE AGE < 22"))))
    (t/is (= ["Ivan"]
             (map :name (query "SELECT PERSON.NAME,PERSON.AGE FROM PERSON WHERE AGE <= 21")))))

  (t/testing "order by"
    (t/is (= [{:name "Ivan"}
              {:name "Malcolm"}]
             (query "SELECT PERSON.NAME FROM PERSON ORDER BY AGE ASC")))
    (t/is (= [{:name "Malcolm"}
              {:name "Ivan"}]
             (query "SELECT PERSON.NAME FROM PERSON ORDER BY AGE DESC")))))

(t/deftest test-equality-of-columns
  (f/transact! *api* [{:crux.db/id :crux.sql.schema/person
                       :crux.sql.table/name "person"
                       :crux.sql.table/query {:find ['id 'name 'surname]
                                              :where [['id :name 'name]
                                                      ['id :surname 'surname]]}
                       :crux.sql.table/columns {'id :keyword, 'name :varchar, 'surname :varchar}}])
  (f/transact! *api* (f/people [{:crux.db/id :ivan :name "Ivan" :surname "Ivan"}
                                {:crux.db/id :malcolm :name "Malcolm" :surname "Sparks"}]))
  (t/is (= [{:name "Ivan"}]
           (query "SELECT PERSON.NAME FROM PERSON WHERE NAME = SURNAME"))))

(t/deftest test-query-for-null
  (f/transact! *api* [{:crux.db/id :crux.sql.schema/person
                       :crux.sql.table/name "person"
                       :crux.sql.table/query {:find ['id 'name 'surname]
                                              :where [['id :name 'name]
                                                      ['id :surname 'surname]]}
                       :crux.sql.table/columns {'id :keyword, 'name :varchar 'surname :varchar}}])
  (f/transact! *api* (f/people [{:crux.db/id :ivan :name "Ivan" :surname nil}
                                {:crux.db/id :malcolm :name "Malcolm" :surname "Sparks"}]))
  (t/is (= [{:name "Ivan"}]
           (query "SELECT PERSON.NAME FROM PERSON WHERE SURNAME IS NULL"))))

(t/deftest test-simple-joins
  (f/transact! *api* [{:crux.db/id :crux.sql.schema/person
                       :crux.sql.table/name "person"
                       :crux.sql.table/query {:find ['id 'name 'planet]
                                              :where [['id :name 'name]
                                                      ['id :planet 'planet]]}
                       :crux.sql.table/columns {'id :keyword, 'name :varchar 'planet :varchar}}
                      {:crux.db/id :crux.sql.schema/planet
                       :crux.sql.table/name "planet"
                       :crux.sql.table/query {:find ['id 'name 'climate]
                                              :where [['id :name 'name]
                                                      ['id :climate 'climate]]}
                       :crux.sql.table/columns {'id :keyword, 'name :varchar 'climate :varchar}}])
  (f/transact! *api* (f/people [{:crux.db/id :person/ivan :name "Ivan" :planet "earth"}
                                {:crux.db/id :planet/earth :name "earth" :climate "Hot"}]))
  (t/testing "retrieve data"
    (t/is (= [{:name "Ivan" :planet "earth"}]
             (query "SELECT PERSON.NAME,PLANET.NAME as PLANET FROM PERSON INNER JOIN PLANET ON PLANET = PLANET.NAME")))))

(t/deftest test-table-backed-by-query
  (f/transact! *api* [{:crux.db/id :crux.sql.schema/person
                       :crux.sql.table/name "person"
                       :crux.sql.table/query {:find ['id 'name 'planet]
                                              :where [['id :name 'name]
                                                      ['id :planet 'planet]
                                                      ['id :planet "earth"]]}
                       :crux.sql.table/columns {'id :keyword, 'name :varchar 'planet :varchar}}])
  (f/transact! *api* (f/people [{:crux.db/id :person/ivan :name "Ivan" :planet "earth"}
                                {:crux.db/id :person/igor :name "Igor" :planet "not-earth"}]))
  (t/testing "retrieve data"
    (t/is (= #{{:id ":person/ivan", :name "Ivan", :planet "earth"}}
             (set (query "SELECT * FROM PERSON"))))))

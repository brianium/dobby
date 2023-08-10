(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'com.github.brianium/dobby)

(def class-dir "target/classes")

(def basis (b/create-basis {:project "deps.edn"}))

(def version (:version basis))

(def jar-file (format "target/%s.jar" (name lib)))

(defn clean [_]
  (doseq [path ["target"]]
    (b/delete {:path path})))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis basis
                :src-dirs ["src"]
                :scm {:url                 "https://github.com/brianium/dobby"
                      :connection          "scm:git:git://github.com/brianium/dobby.git"
                      :developerConnection "scm:git:git://github.com/brianium/dobby.git"
                      :tag                 "HEAD"}})
  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

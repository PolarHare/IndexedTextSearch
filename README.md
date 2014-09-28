IndexedTextSearch
=================

Indexed search of words in files.
With taking into account morphology (english and russian).

Runs arguments
=================
Indexing example run:
```
java -Xmx16g Indexer -j2 -f index.ser ~/Downloads/By.web/
```
Where:
```
-j2 - to run in 2 threads (in fact - two threads is optimal, so at default - two threads used).
-f index.ser - name of file to save index in.
~/Downloads/By.web/ - directory, to be recursively indexed. (Many directories and files can be passed as arguments)
```
  
Searching example run:
```
java Searcher index.ser
```
Where:
```
index.ser - name of file, where index was saved.
```


Example queries:
```
"car OR машина"
"автомобиль"
"пралыоравафы"
"автомобиль AND каско"
"зенит OR бенфика OR монако OR байер"
"(зенит AND бенфика) OR (монако AND байер)"
"car"
```

Example documents collection
=================
Example big documents collection: https://yadi.sk/d/sy4qfmK0LKBva

Size of archive - 2.4 Gb, after extraction ~16 Gb.

Indexing on this big collections was tested on this machine:
```
processor: i7 U3517 (indexing was performed in two threads)
memory: 10 Gb
disk: SSD
```

Some results:
```
JVM arguments for indexing: -Xmx16g
Index creation time: ~15 minutes
Index size on drive: 181 Mb
Index saving on SSD drive (serialization): ~30 seconds
Index loading from SSD drive (deserialization): ~15 seconds
To load index by searcher - around 2Gb of RAM required.
```

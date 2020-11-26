

CSP for scala: implementation, papers, ect ..


* scala-gopher:  this project https://github.com/rssh/scala-gopher 

* Early attempt of message-passsssing concurrency for scala use byte-code transformation:
                                                                     http://wotug.org/papers/CPA-2013/Bate13/Bate13.pdf

* Communicating Scala Object (revised)
          http://www.cs.ox.ac.uk/people/bernard.sufrin/personal/CSO/cpa2008-cso-2014revision.pdf
          // https://www.cs.ox.ac.uk/people/bernard.sufrin/personal/CSO/cpa2008-cso.pdf is unavailable now.

* ACP (Algebra of Communicated Processes) for scala: https://code.google.com/p/subscript/

* Minimalistics implementation of clojure core-async in scala: http://blog.podsnap.com/scasync.html
  (related code: https://github.com/ververve/channels )

* implementation over hawtdipath: https://github.com/oplohmann/CoSelect.Scala

Related

* PiLib is an implementation of \pi-calculus with scala as a hosted language: http://lampwww.epfl.ch/~cremet/publications/pilib.pdf  
```
@INPROCEEDINGS{Cremet03pilib:a,
    author = {Vincent Cremet and Martin Odersky},
    title = {PiLib: A hosted language for pi-calculus style concurrency},
    booktitle = {In Dagstuhl proc.: Domain-Specific Program Generation},
    year = {2003}
}

```

```
@inproceedings{Prokopec:2015:ICE:2814228.2814245,
 author = {Prokopec, Aleksandar and Odersky, Martin},
 title = {Isolates, Channels, and Event Streams for Composable Distributed Programming},
 booktitle = {2015 ACM International Symposium on New Ideas, New Paradigms, and Reflections on Programming and Software (Onward!)},
 series = {Onward! 2015},
 year = {2015},
 isbn = {978-1-4503-3688-8},
 location = {Pittsburgh, PA, USA},
 pages = {171--182},
 numpages = {12},
 url = {http://doi.acm.org/10.1145/2814228.2814245},
 doi = {10.1145/2814228.2814245},
 acmid = {2814245},
 publisher = {ACM},
 address = {New York, NY, USA},
 keywords = {actors, distributed, event streams, isolates},
} 
```

------------------
  part of [scala-gopher](https://github.com/rssh/scala-gopher)

ARD screenscraper
=================
This project uses [TagSoup](http://home.ccil.org/~cowan/XML/tagsoup/) to
screen scrape the ARD video library, searching for a show and a title keyword,
displaying the highest-quality file to download. If you don't speak German,
that isn't very useful, but the screenscraping stuff is informative.

compile with

scalac -cp jars/tagsoup-1.2.1.jar ArdScraper.scala

Example usage:

scala -cp .:jars/tagsoup-1.2.1.jar Weekend "Tatort" "Granit"

output:

http://mvideos.daserste.de/videoportal/Film/c_370000/374425/format579609.mp4

which you can then fetch, for example, with curl -O

Deutsch
-------
Ein kleines Programm, das die ARD-Mediathek nach Sendungstitel und Stichwort
durchforstet, so dass man dann das komplette Video herunterladen kann.
Nützlich, wenn man mal was aufnehmen will, oder mal einen Krimi vor 20 Uhr
schauen will (passiert mir oft ;-))

Getestet mit Scala 2.10.3, benutzt die coole [TagSoup-Bibliothek](http://home.ccil.org/~cowan/XML/tagsoup/).

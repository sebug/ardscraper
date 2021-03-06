// Inspirations:
// http://alvinalexander.com/scala/how-to-load-xml-file-in-scala-load-open-read
// http://home.ccil.org/~cowan/XML/tagsoup/
import scala.io.Source
import java.net.{URL, HttpURLConnection}
import org.ccil.cowan.tagsoup.jaxp.SAXFactoryImpl
import scala.xml.{Elem, XML, InputSource, NodeSeq, Node}
import scala.xml.factory.XMLLoader
import java.io.StringReader
import scala.collection.immutable.Seq
import java.text.SimpleDateFormat
import java.util.Date
import swing._

object ArdScraper {
  val baseUrl = "http://www.ardmediathek.de"

  def getTreeFromURL (urlString : String) = {
    val url = new URL(urlString)
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]
    connection.setDoOutput(true)
    connection.connect()
    val inp = connection.getInputStream()
    val slurped = Source.fromInputStream(inp).getLines().mkString("\n")
    val tree = TagSoupXmlLoader.get().load(new InputSource(new StringReader(slurped)))
    tree
  }

  def getFirstLink [T <: Node](tree : T) (ff : Node => Boolean) : Option[String] = {
    val items = (tree \\ "a").filter(ff).map(_ \ "@href")
    if (items.length == 0) None
    else Some(items(0).text)
  }

  def linkFromURL (url : String) : (Node => Boolean) => Option[String] = {
    getFirstLink(getTreeFromURL(url))
  }

  def switchLinks (url : String) : Seq[String] = {
    val tree = getTreeFromURL(url)
    val sls = (tree \\ "a").filter(n => (n \ "@href").text.indexOf("switch") >= 0)
    sls.map(n => (n \ "@href").text)
  }

  def mediaItems (url : String) : Seq[Node] = {
    val tree = getTreeFromURL(url)
    (tree \\ "div").filter(n => (n \ "@class").text.indexOf("mt-media_item") >= 0)
  }

  def parseMediaItem (mi : Node) : Option[MediaItem] = {
    val hdr = (mi \\ "h3").filter(n => (n \ "@class").text.indexOf("mt-title") >= 0)
    if (hdr.length > 0) {
      val link = (hdr(0) \\ "a" \ "@href").text
      val title = hdr(0).text.trim()

      val atNodes = (mi \\ "span").filter(n => (n \ "@class").text.indexOf("mt-airtime") >= 0)

      val airtime = parseAirTime(atNodes.text)

      if (link.length == 0) {
        None
      } else {
        Some(MediaItem(title, link, airtime))
      }
    } else {
      None
    }
  }

  def parseAirTime (at : String) : Option[AirTime] = {
    try {
      val dateF = new SimpleDateFormat("dd.MM.yy")
      val lenF = new SimpleDateFormat("mm:ss")
      if (at.length == 0) {
        None
      } else {
        val sis = at.split(" ")
        if (sis.length < 2) {
          None
        }
        else {
          val date = dateF.parse(sis(0))
          val len = lenF.parse(sis(1))
          Some(AirTime(date, len))
        }
      }
    } catch {
      case e : Throwable => None
    }
  }

  def parseMediaStream (line : String) : Option[MediaStream] = {
    try {
    val prefix = "mediaCollection.addMediaStream("
    val idx = line.indexOf(prefix)
    if (idx < 0) {
      None
    } else {
      val argsEnd = line.substring(idx + prefix.length)
      val endIdx = argsEnd.indexOf(")")
      if (endIdx < 0) {
        None
      } else {
        val args = argsEnd.substring(0, endIdx).split(",")
        if (args.length < 4) {
          None
        } else {
          val url = args(3).replace("&quot;","").replace("\"","").trim()

          val itemIndex = java.lang.Integer.parseInt(args(0))
          val quality = java.lang.Integer.parseInt(args(1).trim())

          Some(MediaStream(itemIndex, quality, url))
        }
      }
    }
    } catch {
      case e : Throwable => None
    }
  }

  def getMediaItems (sendungName : String) : Seq[MediaItem] = {
      val linkOption = for {
        sendungenLink <- linkFromURL(baseUrl + "/fernsehen") { _.text.indexOf("Sendungen A-Z") >= 0 };
        indexLink <- linkFromURL(baseUrl + sendungenLink) { _.text.indexOf(sendungName(0).toUpper) == 0 };
        sendungLink <- linkFromURL(baseUrl + indexLink) { _.text.indexOf(sendungName) >= 0 }
      } yield sendungLink
      linkOption match {
        case None => Seq.empty
        case Some(sendungLink) =>
          for {
            switchLink <- switchLinks(baseUrl + sendungLink);
            mediaItemNode <- mediaItems(baseUrl + switchLink.replace("view=switch", "view=list"));
            mediaItem <- parseMediaItem(mediaItemNode)
          } yield mediaItem
      }
  }

  def main(args : Array[String]) {
    if (args.length < 2) println("Bitte Sendungsname und Titelstichwort als Argumente eingeben")
    else {
      val titleFilter = args(1)
      for (
        mediaItem <- getMediaItems(args(0))
      ) {
        mediaItem match {
          case MediaItem(title, url, airtime) => {
            if (title.toLowerCase().contains(titleFilter.toLowerCase())) {
              val tree = getTreeFromURL(baseUrl + url)
              val lines = (tree \\ "script").text.split("\n")
              val mediaStreams = for (line <- lines;
                ms <- parseMediaStream(line)) yield ms
              val sorted = mediaStreams.sortBy(-_.quality).sortBy(-_.itemIndex)
              if (sorted.length > 0) {
                sorted(0) match {
                  case MediaStream(_,_,url) => println(url)
                }
              }
            }
          }
        }
      }
    }
  }
}

case class MediaItem (title : String, url : String, airtime : Option[AirTime])

case class AirTime (date : Date, len : Date)

case class MediaStream(itemIndex: Int, quality: Int, url: String)

// http://blog.dub.podval.org/2010/08/scala-and-tag-soup.html
object TagSoupXmlLoader {
  private val factory = new SAXFactoryImpl()

  def get() : XMLLoader[Elem] = {
    XML.withSAXParser(factory.newSAXParser())
  }
}


// Für Papa
object ScraperGUI extends SimpleSwingApplication {
  def top = new MainFrame {
    title = "Download-links für Sendungen finden"
    val sendungNameF = new TextField {
      text = "Tatort"
      columns = 15
    }
    val sendungFilterF = new TextField {
      text = ""
      columns = 15
    }
    val search = Button ("Suchen") {
      printFiltered(sendungNameF.text, sendungFilterF.text)
    }
    contents = new GridPanel(3,2) {
      contents += new Label("Sendungsname: ")
      contents += sendungNameF
      contents += new Label("Zusätzlicher Filter: ")
      contents += sendungFilterF
      contents += search
    }
  }

  def printFiltered(sendungName : String, titleFilter: String) = {
    val mediaStreams = for (
      mi @ MediaItem(title, url, airtime) <- ArdScraper.getMediaItems(sendungName);
      if title.toLowerCase().contains(titleFilter.toLowerCase());
      line <- (ArdScraper.getTreeFromURL(ArdScraper.baseUrl + url) \\ "script").text.split("\n");
      ms <- ArdScraper.parseMediaStream(line);
      if (ms.quality >= 2)
    ) yield (mi, ms)
    val sorted = mediaStreams.sortBy(-_._2.quality).sortBy(-_._2.itemIndex)
    import ListView._
    val view = new ListView(sorted) {
      renderer = Renderer(itm => {
        itm match {
          case (MediaItem(title, _, _), MediaStream(_,quality,url)) =>
            title
        }
      })
    }
    val resultWindow = new Frame {
      title = "Suche nach Sendung: " + sendungName + " Filter: " + titleFilter
      contents = new BorderPanel {
        add(view, BorderPanel.Position.Center)
        add(Button ("Download") {
          download(sorted(view.selection.leadIndex))
        }, BorderPanel.Position.South)
      }
    }
    resultWindow.visible = true
  }

  def download(itm : (MediaItem, MediaStream)) = {
    import sys.process.Process
    itm match {
      case (MediaItem(title, _, _), MediaStream(_,_,url)) => {
        val filename = "/tmp/downloadCmd.command"
        val out = new java.io.FileWriter(filename, false)
        out.write("cd ~/Movies\n")
        out.write("curl -O " + url + "\n")
        out.close

        Process("chmod", Seq("a+x", filename)).run()
        Process("open", Seq("-a", "Terminal", filename)).run()
      }
    }
  }
}


import java.util.zip.ZipFile
import groovy.xml.XmlSlurper
import groovy.xml.MarkupBuilder 

def cli = new CliBuilder(usage: 'convert.groovy')
cli.h(longOpt: 'help', 'print this message')
cli.f(longOpt: 'input-file', args:1, argName:'filename', 'Name of the xgmml file to convert to GPML')
def options = cli.parse(args)

cysFile = "input.cys"

if (options.help) { cli.usage(); System.exit(0) }
if (options.f) cysFile = options.f

def zipfile = new ZipFile(new File(cysFile))
zipfile.entries().findAll { !it.directory }.each {
  if (it.name.endsWith(".xgmml")) {
    xgmml = zipfile.getInputStream(it).text
  }
}

def parser = new XmlSlurper()
def graph = parser.parseText(xgmml);

// determine board
minx = 10000000000; maxx = -10000000000
miny = 10000000000; maxy = -10000000000
graph.node.each { node ->
  x = Double.parseDouble("" + node.graphics.@x[0])
  if (x < minx) minx = x; if (x > maxx) maxx = x
  y = Double.parseDouble("" + node.graphics.@y[0])
  if (y < miny) miny = y; if (y > maxy) maxy = y
}
scaleFactor = 0.5

// output GPML
def builder = new MarkupBuilder()
builder.Pathway(xmlns:'http://pathvisio.org/GPML/2013a', Name:'Cytoscape Import', Organism:'Homo sapiens') {
  width = Double.parseDouble("" + graph.'**'.find { testNode -> testNode.@name[0] == 'NETWORK_WIDTH' }.@value[0]) / 20
  height = Double.parseDouble("" + graph.'**'.find { testNode -> testNode.@name[0] == 'NETWORK_HEIGHT' }.@value[0]) / 20
  Graphics(BoardWidth:''+((maxx-minx)*scaleFactor),  BoardHeight:''+((maxy-miny)*scaleFactor)) {}
  graph.node.each { node ->
    builder.DataNode(TextLabel:node.@label) {
      x = width + (Double.parseDouble("" + node.graphics.@x[0]) - minx) * scaleFactor
      y = height + (Double.parseDouble("" + node.graphics.@y[0]) - miny) * scaleFactor
      Graphics(CenterX:x, CenterY:y, Width:width, Height:height) {}
      Xref(Database:'', ID:'') {}
    }
  }
  InfoBox(CenterX:'0.0', CenterY:'0.0') {}
}
println ""

import java.util.HashMap
import java.util.zip.ZipFile
import groovy.xml.XmlSlurper
import groovy.xml.MarkupBuilder 

def cli = new CliBuilder(usage: 'convert.groovy')
cli.h(longOpt: 'help', 'print this message')
cli.f(longOpt: 'input-file', args:1, argName:'filename', 'Name of the xgmml file to convert to GPML')
cli.s(longOpt: 'sheet', args:1, argName:'sheet', 'name or number of the sheet to read the node and edge info from, e.g. Sheet1')
def options = cli.parse(args)

cysFile = "input.cys"
sheet = "218449"

if (options.help) { cli.usage(); System.exit(0) }
if (options.f) cysFile = options.f
if (options.s) cysFile = options.f

def zipfile = new ZipFile(new File(cysFile))
zipfile.entries().findAll { !it.directory }.each {
  if (it.name.endsWith(".xgmml")) xgmml = zipfile.getInputStream(it).text
  if (it.name.endsWith(sheet + "+shared+edge.cytable")) edgeCVS = zipfile.getInputStream(it).text
  if (it.name.endsWith(sheet + "+shared+node.cytable")) nodeCVS = zipfile.getInputStream(it).text
}

// extract the nodes
skiplines = 5; lineCounter = 0
nodesLabels = new HashMap()
nodesLMIDs = new HashMap()
nodesKEGGs = new HashMap()
nodeCVS.eachLine() { line ->
  lineCounter++
  if (lineCounter > skiplines) {
    cols = line.split(',')
    suid = cols[0].replace("\"","")
    label = cols[1].replace("\"","") // ABBREV
    keggid = cols[10].replace("\"","") // KEGG
    lmid = cols[11].replace("\"","") // LIPIDMAPS
    nodesLabels.put(suid, label)
    if (lmid.length() > 0) nodesLMIDs.put(suid, lmid)
    if (keggid.length() > 0) nodesKEGGs.put(suid, keggid)
  }
}

// extract the edges
interactions = new HashMap()
edgeCVS.eachLine() { line ->
  if (line.contains("(rc)")) {
    line = line.split(',')[4].replace("\"","")
    nodes = line.split(' ')
    if (nodes.length > 2) {
      startNode = nodes[0]
      endNode = nodes[2]
      interactions.put(startNode, endNode)
    }
  }
}

// set up the XGMML input
def graph = new XmlSlurper().parseText(xgmml);

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

// output GPML from XGMML file
def builder = new MarkupBuilder()
builder.Pathway(xmlns:'http://pathvisio.org/GPML/2013a', Name:'Cytoscape Import', Organism:'Homo sapiens') {
  width = Double.parseDouble("" + graph.'**'.find { testNode -> testNode.@name[0] == 'NETWORK_WIDTH' }.@value[0]) / 20
  height = Double.parseDouble("" + graph.'**'.find { testNode -> testNode.@name[0] == 'NETWORK_HEIGHT' }.@value[0]) / 20
  Graphics(BoardWidth:''+((maxx-minx)*scaleFactor),  BoardHeight:''+((maxy-miny)*scaleFactor)) {}
  graph.node.each { node ->
    nodeLabel = ""  + node.@label
    nodeid = node.'@cy:nodeId'
    builder.DataNode(TextLabel:nodeLabel, GraphId:"dn"+nodeid) {
      x = width + (Double.parseDouble("" + node.graphics.@x[0]) - minx) * scaleFactor
      y = height + (Double.parseDouble("" + node.graphics.@y[0]) - miny) * scaleFactor
      Graphics(CenterX:x, CenterY:y, Width:width, Height:height) {}
      if (nodesLMIDs.containsKey("" + nodeid)) Xref(Database:'LIPID MAPS', ID:nodesLMIDs.get("" + nodeid)) {}
      else if (nodesKEGGs.containsKey("" + nodeid)) { Xref(Database:'KEGG Compound', ID:nodesKEGGs.get("" + nodeid)) {} }
      else { Xref(Database:'', ID:'') {} }
    }
  }
  InfoBox(CenterX:'0.0', CenterY:'0.0') {}
}
println ""

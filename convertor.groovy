@Grab(group='io.github.egonw.bacting', module='managers-cdk', version='0.0.22')
@Grab(group='io.github.egonw.bacting', module='managers-inchi', version='0.0.22')

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

def cdk = new net.bioclipse.managers.CDKManager(".");
def inchi = new net.bioclipse.managers.InChIManager(".");

// process the input .cys file
def zipfile = new ZipFile(new File(cysFile))
zipfile.entries().findAll { !it.directory }.each {
  if (it.name.endsWith(".xgmml")) xgmml = zipfile.getInputStream(it).text
  if (it.name.endsWith(sheet + "+shared+edge.cytable")) edgeCVS = zipfile.getInputStream(it).text
  if (it.name.endsWith(sheet + "+shared+node.cytable")) nodeCVS = zipfile.getInputStream(it).text
}

// extract the nodes
skiplines = 5; lineCounter = 0
nodesLabels = new HashMap()
nodesIDs = new HashMap()
nodesLMIDs = new HashMap()
nodesKEGGs = new HashMap()
nodesCHEBIs = new HashMap()
nodesPCIDs = new HashMap()
nodesHMDBs = new HashMap()
nodesSMILESs = new HashMap()
nodeCVS.eachLine() { line ->
  lineCounter++
  if (lineCounter > skiplines) {
    cols = line.split(',')
    suid = cols[0].replace("\"","")
    label = cols[1].replace("\"","") // ABBREV
    shared4names = cols[15].replace("\"","") // shared4names
    smiles = cols[16].replace("\"","") // SMILES
    nodesLabels.put(suid, label)
    nodesIDs.put(shared4names, suid)
    nodesSMILESs.put(suid, smiles)

    // catch identifiers
    lmid = cols[11].replace("\"","") // LIPIDMAPS
    if (lmid.length() > 0) nodesLMIDs.put(suid, lmid)
    keggid = cols[10].replace("\"","") // KEGG
    if (keggid.length() > 0) nodesKEGGs.put(suid, keggid)
    chebid = cols[3].replace("\"","") // ChEBI
    if (chebid.length() > 0) nodesCHEBIs.put(suid, chebid)
    pcid = cols[4].replace("\"","") // CID
    if (pcid.length() > 0) nodesPCIDs.put(suid, pcid)
    hmdb = cols[9].replace("\"","") // HMDB
    if (hmdb.length() > 0) nodesHMDBs.put(suid, hmdb)
  }
}

// extract the edges
interactionStartNode = new HashMap()
interactionEndNode = new HashMap()
interactionComments = new HashMap()
edgeCVS.eachLine() { line ->
  if (line.contains("(rc)")) {
    cols = line.split(',')
    suid = cols[0].replace("\"","")
    comment = cols[2].replace("\"","")
    interactionComments.put(suid, comment)
    line = cols[4].replace("\"","")
    nodes = line.split(' ')
    if (nodes.length > 2) {
      startNode = nodes[0]
      endNode = nodes[2]
      interactionStartNode.put(suid, startNode)
      interactionEndNode.put(suid, endNode)
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
nodesXs = new HashMap()
nodesYs = new HashMap()
builder.Pathway(xmlns:'http://pathvisio.org/GPML/2013a', Name:'Cytoscape Import', Organism:'Homo sapiens') {
  width = Double.parseDouble("" + graph.'**'.find { testNode -> testNode.@name[0] == 'NETWORK_WIDTH' }.@value[0]) / 20
  height = Double.parseDouble("" + graph.'**'.find { testNode -> testNode.@name[0] == 'NETWORK_HEIGHT' }.@value[0]) / 20
  Graphics(BoardWidth:''+((maxx-minx)*scaleFactor),  BoardHeight:''+((maxy-miny)*scaleFactor)) {}

  // the DataNodes
  graph.node.each { node ->
    nodeLabel = ""  + node.@label
    nodeid = node.'@cy:nodeId'
    builder.DataNode(TextLabel:nodeLabel, GraphId:"dn"+nodeid, Type:(nodesSMILESs.containsKey("" + nodeid) ? "Metabolite" : "Unknown")) {
      // additional annotation
      inchikey = null
      if (nodesSMILESs.containsKey("" + nodeid)) {
        try {
          smiles = nodesSMILESs.get("" + nodeid)
          mol = cdk.fromSMILES(smiles)
          anInChI = inchi.generate(mol)
          inchikey = anInChI.getKey()
          builder.Comment("SMILES: " + smiles) {}
        } catch (Exception exception) {
          builder.Comment("Problematic SMILES: " + smiles + "(" + exception.message + ")") {}
        }
      }

      // the grahics
      x = width + (Double.parseDouble("" + node.graphics.@x[0]) - minx) * scaleFactor
      y = height + (Double.parseDouble("" + node.graphics.@y[0]) - miny) * scaleFactor
      nodesXs.put(""+nodeid, x)
      nodesYs.put(""+nodeid, y)
      Graphics(CenterX:x, CenterY:y, Width:width, Height:height, Color:(nodesSMILESs.containsKey("" + nodeid) ? "0000ff" : "000000")) {}

      // the Xref
      if (nodesLMIDs.containsKey("" + nodeid)) Xref(Database:'LIPID MAPS', ID:nodesLMIDs.get("" + nodeid)) {}
      else if (nodesKEGGs.containsKey("" + nodeid)) { Xref(Database:'KEGG Compound', ID:nodesKEGGs.get("" + nodeid)) {} }
      else if (nodesCHEBIs.containsKey("" + nodeid)) { Xref(Database:'ChEBI', ID:nodesCHEBIs.get("" + nodeid)) {} }
      else if (nodesPCIDs.containsKey("" + nodeid)) { Xref(Database:'PubChem-compound', ID:nodesPCIDs.get("" + nodeid)) {} }
      else if (nodesHMDBs.containsKey("" + nodeid)) { Xref(Database:'HMDB', ID:nodesHMDBs.get("" + nodeid)) {} }
      else if (inchikey != null) { Xref(Database:'InChIKey', ID:inchikey) {} }
      else { Xref(Database:'', ID:'') {} }
    }
  }

  // the Interactions
  interactionStartNode.keySet().each() { suid ->
    builder.Interaction(GraphId:'id' + suid) {
      // annotation
      if (interactionComments.containsKey("" + suid)) Comment(interactionComments.get("" + suid)) {}

      // the interaction graphics
      Graphics(ZOrder:"12288", LineThickness:"1.0") {
        startSuid = nodesIDs.get("" + interactionStartNode.get(suid))
        Point(X:nodesXs.get(""+startSuid), Y:nodesYs.get(""+startSuid), GraphRef:'dn' + startSuid) {}
        endSuid = nodesIDs.get("" + interactionEndNode.get(suid))
        Point(X:nodesXs.get(""+endSuid), Y:nodesYs.get(""+endSuid), GraphRef:'dn' + endSuid) {}
      }

      // no identifiers at this moment
      Xref(Database:'', ID:'') {}
    }
  }
  InfoBox(CenterX:'0.0', CenterY:'0.0') {}
}
println ""

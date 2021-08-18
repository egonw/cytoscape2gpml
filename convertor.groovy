
import groovy.xml.XmlSlurper
import groovy.xml.MarkupBuilder 

def cli = new CliBuilder(usage: 'convert.groovy')
cli.h(longOpt: 'help', 'print this message')
cli.f(longOpt: 'input-file', args:1, argName:'filename', 'Name of the xgmml file to convert to GPML')
def options = cli.parse(args)

cyFile = "input.xgmml" // FIXME: I need the full Cytoscape file, because this one does not have the interactions :/

if (options.help) { cli.usage(); System.exit(0) }
if (options.f) cyFile = options.f

def parser = new XmlSlurper()
def graph = parser.parse(cyFile);

def builder = new MarkupBuilder()

builder.Pathway(xmlns:'http://pathvisio.org/GPML/2013a', Name:'Cytoscape Import', Organism:'Homo sapiens') {
  Graphics(BoardWidth:'5000',  BoardHeight:'5000') {}
  graph.node.each { node ->
    builder.DataNode(TextLabel:node.@label) {
      width = Double.parseDouble("" + graph.'**'.find { testNode -> testNode.@name[0] == 'NETWORK_WIDTH' }.@value[0]) / 10
      height = Double.parseDouble("" + graph.'**'.find { testNode -> testNode.@name[0] == 'NETWORK_HEIGHT' }.@value[0]) / 10
      Graphics(CenterX:node.graphics.@x[0], CenterY:node.graphics.@y[0], Width:width, Height:height) {}
      Xref(Database:'', ID:'') {}
    }
  }
  InfoBox(CenterX:'0.0', CenterY:'0.0') {}
}
println ""

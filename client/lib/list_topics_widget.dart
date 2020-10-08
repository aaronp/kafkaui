import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

class ListTopicsWidget extends StatefulWidget {
  static const path = '/topics';

  ListTopicsWidget({Key key}) : super(key: key);

  @override
  _ListTopicsWidgetState createState() => _ListTopicsWidgetState();
}

class _ListTopicsWidgetState extends State<ListTopicsWidget> {
  List<String> topics = [];
  var dontPromptForDelete = false;

  @override
  void initState() {
    super.initState();
    RestClient.listTopics(true).then((value) => setState(() {
          topics = value;
        }));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Topics'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(8.0),
        child: buildList(context),
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }

  buildList(BuildContext context) {
    return CustomScrollView(
      slivers: <Widget>[
        // Add the app bar to the CustomScrollView.
        // SliverAppBar(
        //   // Provide a standard title.
        //   title: Text('${topics.length} topics'),
        //   // Allows the user to reveal the app bar if they begin scrolling
        //   // back up the list of items.
        //   floating: true,
        //   // Display a placeholder widget to visualize the shrinking size.
        //   // flexibleSpace: Placeholder(),
        //   // Make the initial height of the SliverAppBar larger than normal.
        //   expandedHeight: 10,
        // ),
        // Next, create a SliverList
        SliverList(
          // Use a delegate to build items as they're scrolled on screen.
          delegate: SliverChildBuilderDelegate(
            // The builder function returns a ListTile with a title that
            // displays the index of the current item.
            (context, index) => ListTile(
                leading: IconButton(
                    onPressed: () => _deleteTopic(context, topics[index]),
                    color: Colors.red[900],
                    icon: Icon(Icons.remove_circle)),
                title: Text(topics[index])),
            // Builds 1000 ListTiles
            childCount: topics.length,
          ),
        ),
      ],
    );
  }

  Future<void> _doDelete(String topic, bool dontPrompt) {
    return RestClient.deleteTopic(topic).then((value) => setState(() {
          if (value.contains(topic)) {
            topics.remove(topic);
          }
          dontPromptForDelete = dontPrompt;
        }));
  }

  _deleteTopic(BuildContext ctxt, String topic) {
    if (dontPromptForDelete) {
      _doDelete(topic, dontPromptForDelete);
    } else {
      _promptForDelete(ctxt, topic);
    }
  }

  _promptForDelete(BuildContext ctxt, String topic) {

    // show the dialog
    showDialog(
      context: ctxt,
      builder: (parentCtxt) {

        var dontPrompt  = false;
        return StatefulBuilder(
          builder: (context, setState) {


            Widget okButton = RaisedButton.icon(
              icon: Icon(Icons.remove_circle),
              color: Colors.red,
              label: Text("Delete"),
              onPressed: () {
                _doDelete(topic, dontPrompt).then((value) => Navigator.of(context).pop());
              },
            );

            Widget cancelButton = RaisedButton(
              // color: Colors.red[400],
              child: Text("Cancel"),
              onPressed: () {
                Navigator.of(context).pop();
              },
            );

            return AlertDialog(
              title: Text("Confirm Delete"),
              content: Flexible(
                child : Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: Text("Do you really want to delete topic '$topic'?"),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: CheckboxListTile(
                        title: Text("Don't ask me again"),
                        value: dontPrompt,
                        onChanged: (newValue) {
                          setState(() {
                            dontPrompt = !dontPrompt;
                          });
                        },
                        controlAffinity:
                        ListTileControlAffinity.leading, //  <-- leading Checkbox
                      ),
                    )
                  ]
                )
              ),
              actions: <Widget>[
                cancelButton,
                Expanded(child : Container()),
                okButton
              ],
            );
          },
        );
      },
    );

  }

}

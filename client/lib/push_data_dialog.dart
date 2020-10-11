import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'model.dart';
import 'push_data.dart';

class PushDataDialog {
  static void show(BuildContext ctxt, String topic) {
    showDialog(
      context: ctxt,
      builder: (parentCtxt) {
        return StatefulBuilder(
          builder: (context, setState) {
            Widget okButton = RaisedButton.icon(
              icon: Icon(Icons.golf_course),
              color: Colors.red,
              label: Text("Ok"),
              onPressed: () {
// TODO
              },
            );

            Widget cancelButton = RaisedButton(
              child: Text("Cancel"),
              onPressed: () {
                Navigator.of(context).pop();
              },
            );

            return AlertDialog(
              title: Text("Push Data"),
              content: Flexible(
                  child: Column(children: [PushDataWidget(topic)])),
              actions: <Widget>[
                cancelButton,
                Expanded(child: Container()),
                okButton
              ],
            );
          },
        );
      },
    );
  }
}

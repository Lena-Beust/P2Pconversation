# poneytoponey
> Object-based architecture project for MSE ASAD at HES-SO

A peer-to-peer chat system, with simple direct conversations (2 persons). The interface will be a simple interactive CLI.

All clients have to host a RMI registry (started inside the Java app). A central directory must be hosted and used by clients to discover each other. Clients only have to start with a given domain or IP of the directory server, running on port 8080. This directory is a very simple HTTP API [described shortly here](directory-api-spec.md).

## Important files
- [docs/brainstorm.md](docs/brainstorm.md)
- [Report 1](docs/report-1.pdf)
- [Slides 1](docs/slides-1.pdf)

## Local setup
Here are the instructions to run the project locally with multiple clients running in different containers.

```sh
cd project
docker compose up # make sure everything is running
```

The directory is not running !

Open one terminal to start a new client

```sh
docker compose exec client1 bash
# Inside the opened shell, start a new client with hostname of the directory just "directory"
just client directory
```

Open another terminal to start a new client
```sh
docker compose exec client2 bash
# Inside the opened shell, start a new client with hostname of the directory just "directory"
just client directory
```

You can continue with `client3` and `client4` if you want !

## Demo
Here is how you can run a demo with 4 persons joining the network. We have to bypass the school Wifi restrictions to do peer-to-peer communications.
1. Choose a person to be the leader of the demo. This person will host the RMI Registry and manage network.
1. If the Wifi allowing peer-to-peer communications (try to ping another person to see if that's possible) that's fine ! Otherwise, as a leader, you have to to create a Wifi Hotspot and ask the 3 others to join it. The leader should share its IP address in private LAN on the hotspot to the others. Also plug your phone with USB tethering mode to share cellular data to access internet, like an Ethernet cable would give access to Internet.
1. Once everybody is connected to same LAN with ping working in all directions you are ready to start.
1. Everyone build and run the Docker image
    ```
    cd project
    docker build --network host -t poneytoponey-demo .
    docker run --network=host -it -v .:/project poneytoponey-demo
    ```
1. The leader will also run `docker compose up directory` for everyone.
1. All members, except the leader, will first start a tmate session by typing `tmate` and share a SSH read-only link to the leader.
1. Then, everyone will run `just client <ip of the leader>` to indicate the location of the shared directory.
1. The leader can them open 3 other terminal panes with the links of tmate sessions to see everything on one screen. This nice terminal can then be shared on the beamer !

## Game usage
Once started, the game acts like a small shell, with a few commands to do the chat actions. This defines the available commands of our shell.

```sh
Welcome to the PoneyToPoney peer-to-peer system !
Please choose a username to join the network: sam
Joined network as sam.
Available commands:
  join <username>       - join the network as a user
  list                  - list known members of the network
  chat <recipient>      - create or switch to a chat
  chats                 - list chats
  switch <recipient>    - switch to an existing chat
  dump                  - show all messages of the current chat
  send <message>        - send a message to the active chat
  history               - show chat history of current chat
  close <recipient>     - close a chat with a recipient
  refuse <recipient>    - refuse a chat request or discard a chat
  approve <recipient>   - approve a chat request
  status                - show current chat status
  help                  - show this help text
  exit | quit           - leave the shell
```


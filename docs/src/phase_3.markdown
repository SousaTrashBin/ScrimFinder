## Project Contributors

| Name               | Student ID |
|--------------------|------------|
| **Diogo Sousa**    | fc59792    |
| **Bruno Faustino** | fc59784    |
| **Rodrigo Neto**   | fc59850    |

---

## Functional Requirements

> **Detail Filling - Bruno Faustino**

* Direct contact with Riot's API to obtain match and player information;
* Automatic filtering of information to focus more on the user's needs;
* Retrieval of the full raw data to be used for data analysis;
* Retrieval of player information (rank, wins, losses, points, ...);

> **Match History - Bruno Faustino**

* Local store of simplified match information relevant to the user (winning team,
  rank, roles of each player, number of kills, deaths, assists, healing, damage,...,
  per match, per team and per player), that can be inserted either manually, with
  validation, or by providing the match's ID in Riot's API;
* Match filtering based on any criteria the user defines from the simplified
  information, including obtaining all matches of a given player, all matches where
  the player had the most kills, ...;
* Match sorting based on any criteria;
* Match retrieval with paging to avoid fetching enormous amounts of data all at once;
* Soft deletion of matches from the history;
* New match data forwarding to the data analysers;

> **Ranking - Diogo Sousa**

* Real-time calculation and updates of player skill ratings (ELO/MMR) based on match performance;
* Management of global and queue-specific leaderboards with paginated retrieval;
* Automatic player profile initialization and rank mapping via Riot account integration;
* Support for multiple ranking systems tailored to specific queues;

> **Matchmaking - Diogo Sousa**

* Management of the matchmaking queue lifecycle, including ticket creation, status tracking, and cancellation;
* "On Join" matchmaking logic to evaluate and form lobbies immediately upon player entry;
* Implementation of a Standard Queue for 10-player lobby formation;
* Implementation of a Role Queue ensuring team balance across all five roles (TOP, JUNGLE, MID, BOT, SUPP);
* Lobby lifecycle control, including match acceptance/rejection and automatic lobby dissolution on failure;
* Coordination of match states from initial discovery to finalization and result reporting;

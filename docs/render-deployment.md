# Deploying to Render

A first-time, from-scratch walkthrough for putting the whole app live: frontend,
backend, and database. Written for someone who has never used Render before, so it
spells out dashboard clicks rather than assuming familiarity.

## The shape of this deployment

Three separate pieces, on two platforms:

1. **Database** - MySQL on [Aiven](https://aiven.io), not Render. Render's own
   "database" offering is Postgres/Redis; MySQL only exists there as a self-hosted
   Docker template you'd run and manage yourself. Aiven gives a real, fully-managed,
   always-free MySQL instance instead - no compatibility caveats (some other free
   MySQL-ish hosts restrict foreign keys, which this schema uses heavily), no credit
   card, automated backups included.
2. **Backend** (`co-po-backend`) - a Render **Web Service**, built from a Dockerfile
   (Render has no native Java/Maven runtime, so Docker is required for this one).
3. **Frontend** (`co-po-frontend`) - a Render **Static Site** - Render builds it with
   Vite and serves the static output directly, no server or Docker needed for this
   piece.

Build order matters a little: database first, then backend (it needs the database),
then frontend (it needs the backend's URL), then one last trip back to the backend to
tell it the frontend's URL.

**Before starting:** push everything currently in this repo to GitHub - Render deploys
by connecting to a GitHub (or GitLab) repo, not by file upload. This includes the
Dockerfile, `.dockerignore`, and Maven wrapper files added alongside this guide,
without which the backend's Docker build won't have what it needs to run `./mvnw`.

```
git push
```

## Part 1 - Database (Aiven, MySQL, free)

1. Go to [aiven.io](https://aiven.io) and create an account (no card required for the
   free plan).
2. **Create service** → choose **MySQL** → choose the **Free** plan → pick whichever
   cloud/region is closest to you → give it a name (e.g. `co-po-db`) → create it.
3. Wait for its status to turn **Running** (a couple of minutes).
4. Open the service's **Overview** tab. You'll need four things from the connection
   info panel there: **Host**, **Port**, **User**, **Password**, and the default
   database name (Aiven typically calls it `defaultdb`).
5. Build the JDBC URL from those pieces - you'll paste this into the backend's
   environment variables in Part 2:

   ```
   jdbc:mysql://<host>:<port>/<database>?sslMode=REQUIRED
   ```

   Aiven requires TLS for connections; `sslMode=REQUIRED` encrypts the connection
   without needing to import Aiven's CA certificate separately. That's enough here -
   skip it only if you specifically want certificate-pinned verification later.

One thing worth knowing going in: Aiven reserves the right to pause a free service
that sees no initial activity in its first few hours, or no activity for a long
stretch afterward. If the backend ever can't reach the database after a long period
of nobody using the app, check the Aiven service is still running before assuming
something else broke.

## Part 2 - Backend (Render Web Service, Docker)

1. Sign up at [render.com](https://render.com), ideally connecting your GitHub
   account directly so Render can list your repos.
2. **New +** → **Web Service** → select this repo (`SinhaWiz/CO-PO-online`).
3. Configure the service:
   - **Root Directory:** `co-po-backend`
   - **Language/Runtime:** Docker (Render should detect the Dockerfile once it's
     pointed at that root directory; if it asks for a Dockerfile path explicitly,
     it's `Dockerfile`, relative to that root)
   - **Instance Type:** Free is fine to start. Worth knowing up front: free web
     services spin down after 15 minutes with no traffic and take something like
     30-60 seconds to wake back up on the next request (a Java process is slower to
     cold-start than most). If that turns out to be annoying for real users, Starter
     ($7/month) removes the spin-down - an easy thing to upgrade later, not a
     decision to get right today.
4. Add these environment variables (**Environment** tab):

   | Key | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_URL` | the JDBC URL you built in Part 1 |
   | `DB_USERNAME` | the Aiven user (usually `avnadmin`) |
   | `DB_PASSWORD` | the Aiven password |
   | `JWT_SECRET` | a long random string - generate one with `openssl rand -base64 48` and paste the output |

   Leave `CORS_ALLOWED_ORIGINS` unset for now - it defaults to localhost, which
   won't matter until the frontend exists. You'll come back and set it in Part 4.
5. **Create Web Service**. Render pulls the repo, builds the Docker image (this
   takes a few minutes the first time - it's compiling the whole thing), and starts
   the container.
6. Watch the **Logs** tab. You're looking for two things that confirm a clean first
   boot:
   - Flyway applying migrations (`Successfully applied 4 migrations` or similar -
     V1 through V4 all run for real here, since this is a genuinely empty database,
     not one adopted from the desktop app)
   - A line reading `Default admin user created: admin@iut-dhaka.edu / password`
7. Copy the service's URL from the top of the page (something like
   `https://co-po-backend-xxxx.onrender.com`) - you'll need it in Part 3.

## Part 3 - Frontend (Render Static Site)

1. **New +** → **Static Site** → same repo.
2. Configure:
   - **Root Directory:** `co-po-frontend`
   - **Build Command:** `npm run build`
   - **Publish Directory:** `dist`
3. Add one environment variable, using the backend URL from Part 2:

   | Key | Value |
   |---|---|
   | `VITE_API_BASE_URL` | `https://co-po-backend-xxxx.onrender.com/api` |

   (Vite bakes this in at build time, so it has to be set before the first build, not
   after.)
4. **Create Static Site** and wait for the build to finish.
5. Once it's live, add the client-side routing rewrite - without this, refreshing
   the browser on any page other than the homepage (e.g. `/faculty/dashboard`) 404s,
   because there's no `/faculty/dashboard` file on disk, only `index.html` and
   React Router figuring out the rest client-side. In the site's **Redirects/Rewrites**
   tab, add:

   | Source | Destination | Action |
   |---|---|---|
   | `/*` | `/index.html` | Rewrite |

6. Copy this site's URL too (e.g. `https://co-po-frontend-xxxx.onrender.com`).

## Part 4 - Close the loop: tell the backend about the frontend

Back on the backend service from Part 2 → **Environment** → add:

| Key | Value |
|---|---|
| `CORS_ALLOWED_ORIGINS` | `https://co-po-frontend-xxxx.onrender.com` |

Saving this triggers a redeploy (no rebuild needed, just a restart with the new env
var). Once it's back up, the frontend's requests to the backend will pass CORS.

## First login - do this immediately

The backend seeds a default account the very first time it starts against an empty
database: **`admin@iut-dhaka.edu` / `Password123`**. That's fine for local development,
where nobody else can reach it - it is **not** fine sitting on a public
`onrender.com` URL, since anyone who finds it (including by just guessing, given
it's a known default) logs in as super admin.

Log in with it right away and change the password immediately through the account
management screen, before doing anything else with this deployment.

## Known limitations worth knowing about now

- **Generated reports don't persist across restarts.** PDFs and Excel exports get
  written to local disk (`ReportStorageService`), and a Render web service's
  filesystem is ephemeral by default - every redeploy or restart wipes it. Fine for
  trying the app out; if you need generated reports to survive, Render's paid
  instance types support attaching a persistent **Disk**, which you'd mount and point
  `REPORT_STORAGE_DIR` at. Not set up here since it adds cost and this deployment
  hasn't been asked to solve long-term file storage yet - a deliberate "not now,"
  not an oversight.
- **Free-tier cold starts**, covered above.
- **Aiven's free MySQL caps out at 1GB storage**, single node, no read replicas -
  plenty for trying this out or running it for a small department, not something to
  assume scales indefinitely.

## Optional: AI feedback summarization (Vertex AI)

The AI summarization feature (Summary Report's "Summarize with AI" button, course
summaries) already fails gracefully without any GCP setup - it just returns the raw
text back with an explanatory note instead of erroring out. Skip this section
entirely for a first deployment; nothing above depends on it.

If you do want it working later: it authenticates via Google's Application Default
Credentials, which on Render means uploading a GCP service account key as a
**Secret File**. In GCP Console, create a service account with the "Vertex AI User"
role, download its JSON key, then on the backend service's Environment tab: **Secret
Files** → add it (Render mounts it at `/etc/secrets/<filename>`) → then add an
environment variable `GOOGLE_APPLICATION_CREDENTIALS` pointing at that path.

Worth knowing before investing time here: the backend currently expects the
`projectId`/`location` for each request to come from the frontend request itself
(defaulting to a placeholder `"dummy-project"` if not supplied), not from an
environment variable - so wiring this all the way through also means the frontend
actually has real values to send, which is a pre-existing gap in the feature, not
something introduced or fixed by this deployment guide.

## Cost summary

Everything above can run at **$0/month**: Aiven's MySQL free tier, Render's free web
service instance, and Render's static sites are free. The only thing that costs money
is upgrading the backend off the free instance type (from $7/month) if cold starts
become a real problem, or adding a persistent Disk (~$0.25/GB/month) if generated
reports need to survive restarts. Neither is required to get the app running.

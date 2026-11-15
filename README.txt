DonutAlerts - Fabric mod for Minecraft 26.3  (needs Java 25 + Fabric API 26.3)

Menus (keys changeable in Options > Controls > DonutAlerts):
  /donutalerts            main menu            (K)
  /donutalerts move       move / resize boxes  (J)
  /donutalerts auction    auction menu         (L)   also /bidding
  /donutalerts queue      gamble queue menu    (key unbound by default)
  Queue binds (set in Controls > DonutAlerts, unbound by default):
     they WIN  = /donutalerts win   (green + payout, stays until NEXT)
     NEXT      = /donutalerts next
     they LOSE = /donutalerts lose  (red for 1 second, then next)
  /donutalerts clear      empty the queue
  /bidding stop           stop the auction

BUILD (GitHub builds it, nothing to install):
 1. New repository on github.com (e.g. donutalerts-263).
 2. Upload build.gradle, settings.gradle, gradle.properties and the src folder.
 3. Add file > Create new file, name it  .github/workflows/build.yml  and paste the build.yml text.
 4. Actions tab > wait for the green check > download DonutAlerts-26.3-jar > unzip > put the .jar in Lunar's 26.3 Fabric mods folder.

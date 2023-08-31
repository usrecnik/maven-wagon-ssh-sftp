# maven-wagon-ssh-sftp
Apache Maven Wagon implementating using ***strictly*** sftp utility

This project is solving the same issue as existing [Apache Maven Wagon ssh-external](https://maven.apache.org/wagon/wagon-providers/wagon-ssh-external/) but it supports
following sshd configuration (which ssh-external, at the time of writing this readme, does not):

    Match group maven
        ChrootDirectory /path/to/repo/
        X11Forwarding no
        AllowTcpForwarding no
        AllowAgentForwarding no
        ForceCommand internal-sftp

Official [ssh-external](https://maven.apache.org/wagon/wagon-providers/wagon-ssh-external/) uses `scp` utility to upload files and then `ssh` to change ownership of uploaded files. But,

* `ssh` command cannot be used because of `ForceCommand internal-sftp` which forbids anything else than sftp.
* `scp` utility requires OpenSSH version >= 9.x to work with `ForceCommand internal-sftp` which causes issues with older clients

Thus, my implementation simply relies only on `/usr/bin/sftp`, which works with mentioned `ForceCommand internal-sftp`.

I invite developers of ssh-external to merge or otherwise use this idea or my source code in official ssh-external project.

Disclaimer, this code:

* is only tested in my own environment
* it needs a little bit of work to be usable on Windows
* it comes without any unit tests

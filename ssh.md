# SSH 

## Enter on machine with Public IPv4 `ip`

    ssh ubuntu@ip

## For this project you might wanna have Java, maybe Maven

    sudo apt update -y && sudo apt upgrade -y
    sudo apt install -y default-jre


## Allow Password Authentication

Edit the /etc/ssh/sshd_config and modify or add the following line:

    PasswordAuthentication yes


Maybe under /etc/ssh/ssh_config.d/


Restart the SSH server for the new configuration to take effect:

    sudo /etc/init.d/ssh force-reload
    sudo /etc/init.d/ssh restart

Or you might wanna try this if the systemctl service is present

    sudo systemctl restart sshd

## Change username password

    sudo passwd ubuntu


 
# Debian

    sudo apt install socat logrotate init-system-helpers adduser -y

    wget https://github.com/rabbitmq/rabbitmq-server/releases/download/v3.10.7/rabbitmq-server_3.10.7-1_all.deb

    sudo dpkg -i rabbitmq-server_3.10.7-1_all.deb

    sudo apt --fix-broken install -y


# Arch

    sudo pacman -S rabbitmq


--------------------------------------------------

# Configure RabbitMQ

## Service
    sudo systemctl start rabbitmq.service
    sudo systemctl enable rabbitmq.service

## User and Permissions
    sudo rabbitmqctl add_user admin password
    sudo rabbitmqctl set_user_tags admin administrator
    sudo rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"

## Use Dashboard

    rabbitmq-plugins enable rabbitmq_management
    http://localhost:15672/

## Use Api

    http://localhost:15672/api/queues/%2F?columns=name
    http://localhost:15672/api/exchanges/%2F/group?columns=name
    http://localhost:15672/api/exchanges/%2F/group/bindings/source?columns=destination



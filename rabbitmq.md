 
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

## Notes
The default AMQP port used by RabbitMQ is 5672.
Is the primary port used for AMQP communication between RabbitMQ and its **clients**.

RabbitMQ management plugin will use port 15672 by default

# Queue Mirroring

- See: How to Check if a Queue is Mirrored?
https://www.rabbitmq.com/docs/3.13/ha#what-is-mirroring


This mirrors every single queue to all other cluster nodes
- rabbitmqctl set_policy queue-mirror-all "." '{"ha-mode":"all"}'

# Manual Clustering 

rabbit-1 rabbitmqctl cluster_status

#join node 2

rabbit-2 rabbitmqctl stop_app

rabbit-2 rabbitmqctl reset

rabbit-2 rabbitmqctl join_cluster rabbit@rabbit-1

rabbit-2 rabbitmqctl start_app

rabbit-2 rabbitmqctl cluster_status

#join node 3
rabbit-3 rabbitmqctl stop_app

rabbit-3 rabbitmqctl reset

rabbit-3 rabbitmqctl join_cluster rabbit@rabbit-1

rabbit-3 rabbitmqctl start_app

rabbit-3 rabbitmqctl cluster_status


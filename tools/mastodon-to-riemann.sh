#! /bin/sh
riemann_host="10.243.42.34"

stats="$(curl -s https://trunk.mad-scientist.club/api/v1/instance | jq -c .stats)"
user_count="$(echo $stats | jq -r .user_count)"
status_count="$(echo $stats | jq -r .status_count)"
domain_count="$(echo $stats | jq -r .domain_count)"

riemann-client send -h trunk.mad-scientist.club -S mastodon/user-count -i $user_count ${riemann_host}
riemann-client send -h trunk.mad-scientist.club -S mastodon/status-count -i $status_count ${riemann_host}
riemann-client send -h trunk.mad-scientist.club -S mastodon/domain-count -i $domain_count ${riemann_host}

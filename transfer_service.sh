#!/bin/sh
source ~/.bash_profile

# ---------------------------------------------------------------------------
# Transfer batch service process manager
# ---------------------------------------------------------------------------
APP_JAR='/app/app/com-batch-transfer-company_three-SNAPSHOT.jar'
WORK_DIR='/app/app'
SELF_START_JOB="cd ${WORK_DIR};source ~/.bash_profile;./transfer_service.sh start"
SELF_LEGACY_JOB="cd ${WORK_DIR}/;source ~/.bash_profile;./transfer_service.sh start"
GUARD_CRON="*/10 * * * * ${SELF_START_JOB}"

usage() {
    echo "Usage: $(basename "$0") start | stop | status | restart" >&2
}

current_pid() {
    ps -ef | grep "${APP_JAR}" | grep -v grep | awk '{print $2}'
}

drop_cron() {
    (crontab -l | grep -v "$1") | crontab -
}

add_guard_cron() {
    (crontab -l | grep -v "$GUARD_CRON"; echo "$GUARD_CRON") | crontab -
}

boot() {
    pid=$(current_pid)
    if [ -z "${pid}" ]; then
        nohup java -noverify -jar "${APP_JAR}" > /dev/null 2>&1 &
        pid=$(current_pid)
        echo "[pid: ${pid}] start successfully!"
        drop_cron "$SELF_START_JOB"
        add_guard_cron
    else
        echo "${APP_JAR}[${pid}] is running ..."
    fi
}

shutdown() {
    pid=$(current_pid)
    drop_cron "$SELF_LEGACY_JOB"
    if [ -z "${pid}" ]; then
        echo "${APP_JAR} is not running."
        exit 0
    fi
    kill -9 ${pid}
    if [ $? -eq 0 ]; then
        echo "[${pid}]stop successfully!"
    else
        echo "stop fail!"
        exit 1
    fi
}

probe() {
    pid=$(current_pid)
    if [ -z "${pid}" ]; then
        echo "${APP_JAR} is not running."
        exit 0
    fi
    echo "${APP_JAR}[${pid}] is running ..."
}

relaunch() {
    echo "Restarting ${APP_JAR} ..."
    pid=$(current_pid)
    if [ -n "${pid}" ]; then
        kill -9 ${pid}
        if [ $? -eq 0 ]; then
            echo "[${pid}] stop successfully!"
        else
            echo "stop fail!"
            exit 1
        fi
    else
        echo "${APP_JAR} is not running, starting directly ..."
    fi

    drop_cron "$SELF_LEGACY_JOB"

    sleep 2
    nohup java -noverify -jar "${APP_JAR}" > /dev/null 2>&1 &
    new_pid=$(current_pid)
    echo "[pid: ${new_pid}] start successfully!"

    drop_cron "$SELF_START_JOB"
    add_guard_cron
}

if [ $# -eq 0 ]; then
    usage
    exit 1
fi

case "$1" in
    "start" )
        boot
        ;;
    "stop" )
        shutdown
        ;;
    "status" )
        probe
        ;;
    "restart" )
        relaunch
        ;;
    * )
        usage
        ;;
esac

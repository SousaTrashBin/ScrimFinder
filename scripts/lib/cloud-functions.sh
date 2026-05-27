#!/usr/bin/env bash

DETAIL_FILLING_FUNCTIONS=(
    "detail_filling_service|getFilledMatch|RIOT_API_KEY"
    "detail_filling_service|getRawMatchData|RIOT_API_KEY"
    "detail_filling_service|getFilledPlayer|RIOT_API_KEY"
)

functions_build_service_account() {
    local project_id="$1"
    local project_number

    project_number="$(gcloud projects describe "$project_id" --format="value(projectNumber)")"
    printf 'projects/%s/serviceAccounts/%s-compute@developer.gserviceaccount.com\n' \
        "$project_id" \
        "$project_number"
}

ensure_riot_api_secret() {
    local project_id="$1"
    local riot_api_key="$2"

    echo "ensuring Secret Manager secret exists for serverless functions..."
    if ! gcloud secrets describe RIOT_API_KEY --project="$project_id" >/dev/null 2>&1; then
        gcloud secrets create RIOT_API_KEY \
            --project="$project_id" \
            --replication-policy=automatic
    fi

    printf '%s' "$riot_api_key" | gcloud secrets versions add RIOT_API_KEY \
        --project="$project_id" \
        --data-file=-
}

package_detail_filling_function_source() {
    local root_dir="$1"
    local use_redis="${2:-false}"
    shift 2

    echo "packaging services with serverless functions..."

    (
        cd "${root_dir}/detail_filling_service"

        if [ "$use_redis" = "true" ]; then
            ./redisInit.sh || true
        fi

        mvn "$@"

        if [ "$use_redis" = "true" ]; then
            ./redisShutdown.sh || true
        fi
    )
}

deploy_detail_filling_functions() {
    local root_dir="$1"
    local region="$2"
    local build_service_account="$3"
    local runtime_service_account="${4:-}"

    echo "deploying serverless functions in parallel..."

    local deploy_pids=()
    local deploy_names=()

    for service_function in "${DETAIL_FILLING_FUNCTIONS[@]}"; do
        local temp function
        temp="${service_function#*|}"
        function="${temp%%|*}"

        (
            local service
            service="${service_function%%|*}"

            local service_account_args=()
            if [ -n "$runtime_service_account" ]; then
                service_account_args=(--service-account="$runtime_service_account")
            fi

            local attempt max_attempts backoff
            attempt=1
            max_attempts=6
            backoff=5
            while [ "$attempt" -le "$max_attempts" ]; do
                local deploy_output
                deploy_output="$(
                    gcloud functions deploy "$function" \
                        --region="$region" \
                        --entry-point=io.quarkus.gcp.functions.http.QuarkusHttpFunction \
                        --runtime=java21 \
                        --trigger-http \
                        --allow-unauthenticated \
                        --source="${root_dir}/${service}/target/deployment" \
                        --min-instances=0 \
                        --max-instances=30 \
                        --memory=512Mi \
                        --cpu=800m \
                        --build-service-account="$build_service_account" \
                        "${service_account_args[@]}" \
                        --set-secrets 'RIOT_API_KEY=RIOT_API_KEY:latest' 2>&1
                )" && break

                echo "$deploy_output" >&2
                if [[ "$deploy_output" == *"status=[409]"* ]] && [[ "$deploy_output" == *"unable to queue the operation"* ]]; then
                    if [ "$attempt" -eq "$max_attempts" ]; then
                        echo "deploy retries exhausted for ${function}" >&2
                        exit 1
                    fi

                    local jitter
                    jitter=$((RANDOM % 4))
                    echo "transient deploy queue contention for ${function}; retrying in $((backoff + jitter))s (attempt ${attempt}/${max_attempts})" >&2
                    sleep $((backoff + jitter))
                    backoff=$((backoff * 2))
                    attempt=$((attempt + 1))
                    continue
                fi

                echo "non-retryable deploy failure for ${function}" >&2
                exit 1
            done
        ) &

        deploy_pids+=("$!")
        deploy_names+=("$function")
    done

    local deploy_failed=0
    for i in "${!deploy_pids[@]}"; do
        if ! wait "${deploy_pids[$i]}"; then
            echo "serverless function deploy failed: ${deploy_names[$i]}"
            deploy_failed=1
        fi
    done

    if [ "$deploy_failed" -ne 0 ]; then
        return 1
    fi
}

discover_detail_filling_domain() {
    local region="$1"

    echo "function endpoints:"

    DETAIL_FILLING_FUNCTION_URL=""
    DETAIL_FILLING_DOMAIN=""

    for service_function in "${DETAIL_FILLING_FUNCTIONS[@]}"; do
        local temp function function_url
        temp="${service_function#*|}"
        function="${temp%%|*}"
        function_url="$(gcloud functions describe "$function" --region="$region" --format="value(url)")"

        if [ -z "$DETAIL_FILLING_FUNCTION_URL" ]; then
            DETAIL_FILLING_FUNCTION_URL="$function_url"
            DETAIL_FILLING_DOMAIN="$(echo "$function_url" | awk -F/ '{print $3}')"
        fi

        case "$function" in
            getFilledMatch)
                echo "${function}: ${function_url}/api/v1/riot/matches/{matchId}"
                ;;
            getRawMatchData)
                echo "${function}: ${function_url}/api/v1/riot/matches/{matchId}/raw"
                ;;
            getFilledPlayer)
                echo "${function}: ${function_url}/api/v1/riot/players/{server}/{name}/{tag}"
                ;;
            *)
                echo "${function}: ${function_url}"
                ;;
        esac
    done

    if [ -z "$DETAIL_FILLING_FUNCTION_URL" ]; then
        echo "error: no detail filling serverless function URL was found."
        return 1
    fi

    export DETAIL_FILLING_FUNCTION_URL
    export DETAIL_FILLING_DOMAIN

    echo "using detail filling domain for Traefik ExternalName: ${DETAIL_FILLING_DOMAIN}"
}

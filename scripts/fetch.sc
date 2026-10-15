// Optional inventory courier for ms.sc. Kept separate so a failure here
// cannot prevent the core /ms command tree from loading.

__config() -> {
    'scope' -> 'global',
    'stay_loaded' -> true,
    'command_permission' -> 'players',
    'commands' -> {
        '' -> '_help',
        '<online_fake>' -> 'fetch_here',
        '<online_fake> <target_player>' -> 'fetch_to_player'
    },
    'arguments' -> {
        'online_fake' -> {
            'type' -> 'term',
            'suggester' -> _(args) -> map(
                filter(player('all'), _~'player_type' == 'fake'),
                _~'name'
            )
        },
        'target_player' -> {
            'type' -> 'players',
            'single' -> true
        }
    }
};

_help() -> (
    print('FETCH: /fetch <online_fake> [target_player]');
    null
);

fetch_here(bot) -> _fetch_bot(bot, null, false);

fetch_to_player(bot, target_name) -> _fetch_bot(bot, player(str(target_name)), true);

_fetch_bot(bot, target, should_return) -> (
    bot = str(bot);
    fake = player(bot);
    if(fake == null,
        print('MS: player '+bot+' is not online.'),
        if(fake~'player_type' != 'fake',
            print('MS: '+bot+' is online but is not a fake player.'),
            if(should_return && target == null,
                print('MS: target player is not online.'),
                (
                source = query(fake, 'trace', 5, 'blocks');
                if(source == null || inventory_size(source) == null,
                    print('MS: '+bot+' is not looking at a container.'),
                    (
                        [moved_items, remaining_items] = _collect_container(fake, source);
                        if(remaining_items == 0,
                            print('MS: collected all '+moved_items+' items with '+bot),
                            print('MS: collected '+moved_items+' items; '+remaining_items+' remain because '+bot+' is full.')
                        );
                        if(!should_return,
                            print('MS: '+bot+' stays at the container.'),
                            _return_bot(bot, target)
                        )
                    )
                )
                )
            )
        )
    );
    null
);

_collect_container(fake, source) -> (
    moved_items = 0;
    remaining_items = 0;
    for(range(inventory_size(source)),
        (
            source_slot = _;
            stack = inventory_get(source, source_slot);
            if(stack != null,
                (
                    remaining = _store_stack(fake, stack);
                    moved_items += stack:1-remaining;
                    remaining_items += remaining;
                    inventory_set(source, source_slot, remaining)
                )
            )
        )
    );
    [moved_items, remaining_items]
);

_store_stack(fake, stack) -> (
    item_name = str(stack:0);
    // Shulker boxes may never contain other shulker boxes.
    if(!_is_shulker(item_name) && _put_stack_in_shulker(fake, stack),
        0,
        _put_stack_in_player(fake, stack)
    )
);

_is_shulker(item_name) -> (
    item_name = str(item_name);
    item_name == 'shulker_box' || item_name == 'minecraft:shulker_box' || bool(item_name ~ '_shulker_box$')
);

_put_stack_in_shulker(fake, stack) -> (
    for(range(36),
        (
            box_slot = _;
            box = inventory_get(fake, box_slot);
            if(box != null && _is_shulker(box:0),
                (
                    // parse_nbt deep-converts the item NBT to ordinary Scarpet
                    // maps/lists. Mutate those nested containers directly.
                    box_data = copy(parse_nbt(box:2));
                    components = box_data:'components';
                    if(components == null, components = {});
                    contents = components:'minecraft:container';
                    if(contents == null, contents = [], contents = copy(contents));
                    empty_slot = _empty_container_slot(contents);
                    if(empty_slot != null,
                        (
                            item_data = copy(parse_nbt(stack:2));
                            item_data:'count' = stack:1;
                            wrapper = {
                                'slot' -> empty_slot,
                                'item' -> item_data
                            };
                            // null address means append for an ordinary list.
                            inserted = put(contents, null, wrapper);
                            if(inserted,
                                (
                                    components:'minecraft:container' = contents;
                                    box_data:'components' = components;
                                    inventory_set(fake, box_slot, 1, box:0, encode_nbt(box_data, true));
                                    return(true)
                                )
                            )
                        )
                    )
                )
            )
        )
    );
    false
);

_empty_container_slot(contents) -> (
    used_slots = {};
    for(contents, used_slots:(str(_:'slot')) = true);
    for(range(27), if(used_slots:(str(_)) == null, return(_)));
    null
);

_put_stack_in_player(fake, stack) -> (
    item_name = str(stack:0);
    remaining = stack:1;
    item_limit = stack_limit(item_name);
    // Merge only identical stacks so enchantments/components are preserved.
    for(range(36),
        (
            target_slot = _;
            current = inventory_get(fake, target_slot);
            if(remaining > 0 && current != null && current:0 == stack:0 && str(current:2) == str(stack:2) && current:1 < item_limit,
                (
                    moved = min(remaining, item_limit-current:1);
                    inventory_set(fake, target_slot, current:1+moved);
                    remaining = remaining-moved
                )
            )
        )
    );
    for(range(36),
        (
            target_slot = _;
            if(remaining > 0 && inventory_get(fake, target_slot) == null,
                (
                    moved = min(remaining, item_limit);
                    inventory_set(fake, target_slot, moved, item_name, stack:2);
                    remaining = remaining-moved
                )
            )
        )
    );
    remaining
);

_return_bot(bot, target) -> (
    [tx, ty, tz, tyaw, tpitch] = target~'location';
    target_name = str(target~'name');
    target_dimension = str(target~'dimension');
    run('player '+bot+' kill');
    schedule(1, '_wait_for_exit', bot, target_name, tx, ty, tz, tyaw, tpitch, target_dimension, 40);
    print('MS: returning '+bot+' to '+target_name)
);

_wait_for_exit(bot, target_name, tx, ty, tz, tyaw, tpitch, target_dimension, ticks_left) -> (
    if(player(bot) == null,
        (
            run('player '+bot+' spawn at '+tx+' '+ty+' '+tz+' facing '+tyaw+' '+tpitch+' in '+target_dimension);
            schedule(1, '_wait_for_return', bot, target_name, 400)
        ),
        if(ticks_left > 0,
            (
                run('player '+bot+' kill');
                schedule(1, '_wait_for_exit', bot, target_name, tx, ty, tz, tyaw, tpitch, target_dimension, ticks_left-1)
            ),
            print('MS: failed to disconnect '+bot)
        )
    )
);

_wait_for_return(bot, target_name, ticks_left) -> (
    fake = player(bot);
    if(fake != null && fake~'player_type' == 'fake',
        print('MS: returned '+bot+' to '+target_name+' with collected items'),
        if(ticks_left > 0,
            schedule(1, '_wait_for_return', bot, target_name, ticks_left-1),
            print('MS: return timeout '+bot)
        )
    )
);
